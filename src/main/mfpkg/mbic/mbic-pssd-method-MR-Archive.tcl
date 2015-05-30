
# ===========================================================================
# Method for 7T Archive
# =========================================================================== 
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create-MBIC-MR-Archive-method { { action 0 } { fillin 0 } } {
	
	set name "7T Archive for MBC Imaging Unit"
	set description "Method to receive MR data of any modality for generic subject types."
#
	set type1 "Magnetic Resonance Imaging"
	set name1 "MRI acquisition" 
	set desc1 "MRI acquisition of subject" 
#
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
		:project < \
			:public < \
				:metadata < :definition -requirement optional nig-daris:pssd-identity > \
				:metadata < :definition -requirement optional nig-daris:pssd-subject > \
			> \
		> \
	> \
  	:step < :name ${name1} :description ${desc1} :study < :type ${type1}  > >"
     
    # Create/update Method
    set id2 [xvalue id [om.pssd.method.for.subject.update $args]]
    if { $id2 == "" } {
       # An existing Method was updated
       return $id
    } else {
       # A new Method was created
       return $id2
    }
}
