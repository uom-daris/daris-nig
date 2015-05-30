
# ===========================================================================
# Simple method for MBC IU human acquisitions 
# =========================================================================== 
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create-MBIC-Animal-method { { action 0 } { fillin 0 } } {
	
	set name "Animal acquisitions for MBC Imaging Unit"
	set description "Multi-mode imaging acquisitions for animals"
#
	set type1 "Positron Emission Tomography/Computed Tomography"
	set name1 "Combined PET/CT acquisition" 
	set desc1 "PET/CT acquisition of subject" 
#
	set type2 "Positron Emission Tomography"
	set name2 "PET acquisition" 
	set desc2 "PET acquisition of subject"
#
	set type3 "Computed Tomography"
	set name3 "Computed Tomography acquisition"
    set desc3 "CT acquisition of subject"
#
	set type4 "Magnetic Resonance Imaging"
	set name4 "MRI acquisition" 
	set desc4 "MRI acquisition of subject" 
#	
    set type5 "Nuclear Medicine"
    set name5 "Nuclear Medicine acquisition"
    set desc5 "NM acquisition of subject"
#
    set type6 "Dose Report"
    set name6 "Dose Report acquisition"
    set desc6 "SR acquisition of subject"
    #
    set type7 "Unspecified"
    set name7 "Unspecified"
    set desc7 "Allows unspecified study types and modalities to be uploaded also"
    

	set margs ""
	# See if Method pre-exists
	set id [getMethodId $name]
	    
	# Set arguments based on desired action	
	set margs [setMethodUpdateArgs $id $action $fillin]
	if { $margs == "quit" } {
		return
	}
        
	# Set Method body
	set args "${margs} \
	:namespace pssd/methods  \
	:name ${name} \
	:description ${description} \
	:subject < \
	    :human true \
		:project < \
			:public < \
				:metadata < :definition -requirement optional nig-daris:pssd-identity > \
				:metadata < :definition -requirement optional nig-daris:pssd-subject  :value < :type constant(animal) > > \
			    :metadata < :definition -requirement optional nig-daris:pssd-animal-subject > \
				:metadata < :definition -requirement optional nig-daris:pssd-animal-disease > \
			> \
		> \
	> \
	:step < :name ${name1} :description ${desc1} :study < :type ${type1} :dicom < :modality PT :modality CT > > > \
	:step < :name ${name2} :description ${desc2} :study < :type ${type2} :dicom < :modality PT > > > \
	:step < :name ${name3} :description ${desc3} :study < :type ${type3} :dicom < :modality CT > > > \
	:step < :name ${name4} :description ${desc4} :study < :type ${type4} :dicom < :modality MR > > > \
	:step < :name ${name5} :description ${desc5} :study < :type ${type5} :dicom < :modality NM > > > \
	:step < :name ${name6} :description ${desc6} :study < :type ${type6} :dicom < :modality SR > > > \
	:step < :name ${name7} :description ${desc7} :study < :type ${type7}  > >"
	
     
    # Create/update Method
    set id2 [xvalue id [om.pssd.method.for.subject.update $args]]
    if { $id2 == "" } {
       # An existng Method was updated
       return $id
    } else {
       # A new Method was created
       return $id2
    }
}
