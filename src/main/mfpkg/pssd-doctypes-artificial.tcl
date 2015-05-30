
# Defines the basic generic information for an artificial subject 
proc createDocType_pssd_artificial {} {
	asset.doc.type.update \
		:create true :type nig-daris:pssd-artificial-subject \
		:description "Document type for artificial subject types." \
		:label "Artificial subjects" \
		:definition < \
		   :element -name type -type enumeration -enumerated-values "phantom,table tennis ball" \
		     -index true -min-occurs 0 -max-occurs 1 < :description "An artificial subject type." > \
		   :element -name "component" -type "enumeration" -index "true" -min-occurs "0" \
		    < \
                :description "This substance is a component of the subject" \
                :restriction -base "enumeration" <  :value "Agar gel" :value "wire"  > \
            > \
           :element -name "phantom" -type "enumeration" -index "true" -min-occurs "0" -max-occurs "1" \
            < \
              :description "For a phantom, the manufacturer and unique identifier supplied by the manufacturer for a phantom." \
     	      :restriction -base "enumeration" < :dictionary "nig.pssd.phantom" > \
            > \
 		>
}

#===========================================================================#
proc createDocTypesArtificial { } {

	createDocType_pssd_artificial
}

#===========================================================================#
proc destroyDocTypesArtificial { } {

	set doctypes { nig-daris:pssd-artificial-subject }

	foreach doctype $doctypes {
        destroyDocType $doctype "true"
	}
	
}
                                                                           #
#===========================================================================#
createDocTypesArtificial
