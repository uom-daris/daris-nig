
proc update_project { { recursive 0 } } {

    set PROJECT_CID "1.7.32"
    set METHOD_CID "1.4.19"

    # update project
    if { [xvalue asset/meta/daris:pssd-project/method/id [asset.get :cid ${PROJECT_CID}]] != "${METHOD_CID}" } {
        asset.set :cid ${PROJECT_CID} :meta < :daris:pssd-project < :method < :id ${METHOD_CID} > > >
    }

    if { ${recursive} } {
        # update subjects
        foreach subject_cid [xvalues cid [asset.query :where cid in '${PROJECT_CID}' and model='om.pssd.subject' :size infinity :action get-cid]] {
            update_subject ${subject_cid} ${recursive}
        }
    }

}

proc update_subject { subject_cid { recursive 0 } } {

    set METHOD_CID "1.4.19"

    # update subject asset
    if { [xvalue asset/meta/daris:pssd-subject/method [asset.get :cid ${subject_cid}]] != "${METHOD_CID}" } {
        asset.set :cid ${subject_cid} :meta < :daris:pssd-subject < :method ${METHOD_CID} > >
    }

    # update subject template
    if { [lsearch [xvalues asset/template\[@ns='pssd.private'\]/metadata/definition [asset.get :cid ${subject_cid}]] nig-daris:ms-gait-subject] == -1 } { 
        asset.template.set :cid ${subject_cid} \
            :template -ns "pssd.public" < \
                :metadata < :definition -requirement optional nig-daris:pssd-identity > \
                :metadata < :definition -requirement optional nig-daris:pssd-subject  :value -binding default < :type constant(animal) > > \
                :metadata < :definition -requirement optional nig-daris:pssd-animal-subject  :value -binding default < :species constant(human) > > \
                :metadata < :definition -requirement optional nig-daris:pssd-animal-disease > > \
            :template -ns "pssd.private" < \
                :metadata < :definition -requirement optional nig-daris:pssd-human-identity > \
                :metadata < :definition -requirement optional nig-daris:ms-gait-subject > >
    }

    if { ${recursive} } {
        foreach ex_method_cid [xvalues cid [asset.query :where cid in '${subject_cid}' and model='om.pssd.ex-method' :size infinity :action get-cid]] {
            update_ex_method ${ex_method_cid} ${recursive}
        }
    }

}

proc update_ex_method { ex_method_cid { recursive 0 } } {
    
    set OLD_METHOD_CID "1.4.2"
    set METHOD_CID "1.4.19"
    
    set current_method_cid [xvalue asset/meta/daris:pssd-ex-method/method/id [asset.get :cid ${ex_method_cid}]]

    if { ${current_method_cid} != ${METHOD_CID} } {
        if { ${current_method_cid} != ${OLD_METHOD_CID} } {
            error "Unexpected method: ${current_method_cid} found on ex-method: ${ex_method_cid}."
        }
        
        set method_name [xvalue asset/meta/daris:pssd-object/name [asset.get :cid ${METHOD_CID}]]
        set method_description [xvalue asset/meta/daris:pssd-object/description [asset.get :cid ${METHOD_CID}]]

        # update ex-method 
        asset.set :cid ${ex_method_cid} :meta -action replace < \
            :daris:pssd-object < \
                :type ex-method \
                :name "${method_name}" \
                :description "${method_description}" > \
            :daris:pssd-method < \
                :step -id 1 < \
                    :name "MRI acquisition" \
                    :description "MRI acquisition of subject" \
                    :study < \
                        :type "Magnetic Resonance Imaging" \
                        :dicom < :modality MR > > > > \
            :daris:pssd-ex-method < \
                :method < \
                    :id ${METHOD_CID} \
                    :name "${method_name}" \
                    :description "${method_description}" > \
                :state incomplete > >
    }
    if { ${recursive} } {
        # update studies
        foreach study_cid [xvalues cid [asset.query :where cid in '${ex_method_cid}' and model='om.pssd.study' :size infinity :action get-cid]] {
            update_study ${study_cid} ${recursive}
        }
    }
}

proc update_study { study_cid { recursive 0 } } {

    if { [xvalue asset/meta/daris:pssd-study/method/@step [asset.get :cid ${study_cid}]] == "4" } {
        # update study metadata
        asset.set :cid ${study_cid} :meta -action remove < :daris:pssd-study -step 4 > \
            :meta -action add < \
                :daris:pssd-study < \
                    :type "Magnetic Resonance Imaging" \
                    :method -step 1 "1.7.32.1.1" > >
    }

    if { ${recursive} } {
        # update datasets
        foreach dataset_cid [xvalues cid [asset.query :where cid in '${study_cid}' and model='om.pssd.dataset' :size infinity :action get-cid]] {
            update_dataset ${dataset_cid}
        }
    }
}

proc update_dataset { dataset_cid } {

    if { [xvalue asset/meta/daris:pssd-derivation/method/@step [asset.get :cid ${dataset_cid}]] == "4" } {
        # update dataset metadata
        asset.set :cid ${dataset_cid} :meta -action remove < :daris:pssd-derivation -step 4 > \
            :meta -action add < \
                :daris:pssd-derivation < \
                    :processed "false" \
                    :method -step 1 "1.7.32.1.1" > >
    }

}
