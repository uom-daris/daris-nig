# ===========================================================================
# Simple method for Human MRI acquisitions appropriate to standard RCH usage
# With no re-usable RSubject (now deprecated)
# ===========================================================================
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create_CVL_Test_Method { { action 0 } { fillin 0 } } {
	set name "CVL UAT Method"
	set description "Human MRI acquisition with BET workflow"
	#
	set name1 "MRI acquisition and BET workflow" 
	set desc1 "MRI acquisition of subject with BET workflow" 
	set type1 "Magnetic Resonance Imaging"
	#
	set margs ""
	# See if Method pre-exists
	set id [getMethodId $name]
	    
	# Set arguments based on desired action
	set margs [setMethodUpdateArgs $id $action $fillin]
	if { $margs == "quit" } {
		return
	}
	#
	set args "${margs} \
	    :namespace pssd/methods \
	    :name ${name} \
	    :description ${description} \
  	    :subject < \
	      :project < \
		    :public < \
			    :metadata < :definition -requirement optional nig-daris:pssd-identity > \
			    :metadata < :definition -requirement optional nig-daris:pssd-subject :value < :type constant(animal) > > \
			    :metadata < :definition -requirement optional nig-daris:pssd-animal-subject :value < :species constant(human) > > \
			    :metadata < :definition -requirement optional nig-daris:pssd-human-subject > \
		     > \
		   > \
   	     > \
 	    :step < \
		    :name ${name1} :description ${desc1} :study < :type ${type1} :dicom < :modality MR > > \
	    >\
	    :step <\
            :name BET \
            :description BET workflow \
            :transform <\
                :definition -version 0 14 \
                :iterator <\
                    :scope ex-method \
                    :type citeable-id \
                    :query model='om.pssd.study' and mf-dicom-study has value \
                    :parameter pid \
               >\
           >\
        >"
 	set id2 [xvalue id [om.pssd.method.for.subject.update $args]]
	if { $id2 == "" } {
	   # An existing Method was updated
	   return $id
	} else {
	   # A new Method was created
	   return $id2
	}
}