
# ===========================================================================
# Simple method for MBC IU mineral acquisitions 
# =========================================================================== 
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create-MBIC-Mineral-method { { action 0 } { fillin 0 } } {
	
	set name "Mineral CT acquisitions for MBC Imaging Unit"
	set description "CT imaging acquisitions for minerals."
#
	set type1 "Computed Tomography"
	set name1 "Computed Tomography acquisition"
    set desc1 "CT acquisition of subject"
#
    set type2 "Dose Report"
    set name2 "Dose Report acquisition"
    set desc2 "SR acquisition of subject"

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
	    :human false \
		:project < \
			:public < \
				:metadata < :definition -requirement optional nig-daris:pssd-identity > \
				:metadata < :definition -requirement optional nig-daris:pssd-subject  :value < :type constant(mineral) > > \
			    :metadata < :definition -requirement optional nig-daris:pssd-mineral-subject > \
			> \
		> \
	> \
	:step < :name ${name1} :description ${desc1} :study < :type ${type1} :dicom < :modality CT > > > \
	:step < :name ${name2} :description ${desc2} :study < :type ${type2} :dicom < :modality SR > > >"
	
     
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
