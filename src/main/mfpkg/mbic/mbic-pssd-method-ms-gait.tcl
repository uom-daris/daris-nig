
# ===========================================================================
# Simple method MS Gait study at MBC Imaging Unit 
# =========================================================================== 
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create-MBIC-MS-GAIT-method { { action 0 } { fillin 0 } } {
	
	set name "MS Gait Study"
	set description "MBCIU MS Gait Study"
	
    set type1 "Magnetic Resonance Imaging"
	set name1 "MRI acquisition" 
	set desc1 "MRI acquisition of subject" 
    
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
			    :metadata < :definition -requirement optional nig-daris:pssd-animal-subject :value < :species constant(human) > > \
				:metadata < :definition -requirement optional nig-daris:pssd-animal-disease > \
			> \
		    :private < \
			    :metadata < :definition -requirement mandatory nig-daris:pssd-human-identity > \
			    :metadata < :definition -requirement mandatory nig-daris:ms-gait-subject > \
		    > \
		> \
	> \
	:step < :name ${name1} :description ${desc1} :study < :type ${type1} :dicom < :modality MR > > >"
	
     
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
