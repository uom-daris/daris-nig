
# ===========================================================================
# Simple method for generic subjects and standard modalities.
# =========================================================================== 
#
# If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
# If creating Method, fillin - 0 (don't fill in cid allocator space), 1 (fill in cid allocator space)
#
proc create-MBIC-Generic-method { { action 0 } { fillin 0 } } {
	
	set name "Generic acquisitions for MBC Imaging Unit"
	set description "Method to receive PET/CT, SR,NM or MR data for generic subject types."
#
	set type1 "Positron Emission Tomography/Computed Tomography"
	set name1 "Combined PET/CT acquisition" 
	set desc1 "PET/CT acquisition of subject" 
#
	set type4 "Magnetic Resonance Imaging"
	set name4 "MRI acquisition" 
	set desc4 "MRI acquisition of subject" 
#
    set type2 "Dose Report"
    set name2 "Dose Report acquisition"
    set desc2 "SR acquisition of subject"
#
    set type3 "Nuclear Medicine"
    set name3 "Nuclear Medicine acquisition"
    set desc3 "NM acquisition of subject"
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
	:step < :name ${name1} :description ${desc1} :study < :type ${type1} :dicom < :modality PT :modality CT > > > \
    :step < :name ${name2} :description ${desc2} :study < :type ${type2} :dicom < :modality SR > > > \
	:step < :name ${name3} :description ${desc3} :study < :type ${type3} :dicom < :modality NM > > > \
  	:step < :name ${name4} :description ${desc4} :study < :type ${type4} :dicom < :modality MR :modality OT > > >"
     
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
