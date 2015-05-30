# ===========================================================================
# Simple method for MBC IU Quality Assurance
# =========================================================================== 
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create-MBIC-QA-method { { action 0 } { fillin 0 } } {
	
	set name "Quality Assurance (artificial Q/A subject) acquisitions for MBC Imaging Unit"
	set description "Quality assurance imaging acquisitions using an artificial Q/A subject type."
#
	set type1 "Quality Assurance"
	set name1 "Quality assurance acquisition" 
	set desc1 "Quality assurance acquisition of artificial (e.g. internal or external phantom) subject" 
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
	    :human false \
		:project < \
			:public < \
				:metadata < :definition -requirement optional nig-daris:pssd-identity > \
				:metadata < :definition -requirement optional nig-daris:pssd-subject  :value < :type constant(artificial) > > \
				:metadata < :definition -requirement optional nig-daris:pssd-artificial-subject > \
			> \
		> \
	> \
	:step < :name ${name1} :description ${desc1} :study < :type ${type1}  > >"
	
     
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
