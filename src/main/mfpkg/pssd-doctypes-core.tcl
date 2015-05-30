#=============================================================================
# This script creates core  Document Types for meta-data to be attached to PSSD
# objects.  These Document Types should be Method independent and hence reusable.
# Non-reusable and/or very specific Document Types created for specific Methods
# should not be included in this file.
#
#
# Document Type Object Model:
#
#                 Subject
#        Animal            Non-Animal
#   Human                Plant     Mineral
#
# Note: you need to create the dictionaries first because some of  the
# doc types requires them.
#=============================================================================



#=============================================================================
# Subject-related Document Types: nig-daris:pssd-subject, nig-daris:pssd-human-subject,
# nig-daris:pssd-animal-subject, nig-daris:pssd-identity, nig-daris:pssd-human-identity
#
# These meta-data should be placed on  the Subject object
# Should be paired with the human and animal specific Doc Types
proc createDocType_pssd_subject {} {

	asset.doc.type.update \
		:create true :type nig-daris:pssd-subject \
		:description "Document type for project-based subject" \
		:label "Subject" \
		:definition < \
		:element -name type  -min-occurs 1 -max-occurs 1 \
		-type enumeration -enumerated-values animal,vegetable,mineral,artificial,internal,unknown \
		-index true -case-sensitive false < \
		:description "Type of subject.  Artificial might be used for, e.g. a phantom in an MR scanner. Internal might be used for an internally generated instrument system test (e.g. q/a).." > \
		:element -name control -min-occurs 0 -max-occurs 1 -type boolean -index true  < \
		:description "Subject is a member of a control group" > \
		>

}

#=============================================================================
# Generally, human animal subjects are re-used and so these meta-data should
# be placed on the R-Subject object
# Generally, non-human animals subjects are not re-used and so these meta-data
# should be placed on the Subject object
# Should be paired with nig-daris:pssd-subject
#   :element -name "birthDate" -type "date" -index "true" -min-occurs "0" -max-occurs "1" \
#    < \
#      :description "Birth date of the subject" \
#      :restriction -base "date" \
#      < \
#        :time "false" \
#      > \
#    > \

proc createDocType_pssd_animal_subject {} {

asset.doc.type.update :create yes :type nig-daris:pssd-animal-subject \
  :label "nig-daris:pssd-animal-subject" \
  :description "Document type for an animal (Humans included) subject" \
  :definition < \
    :element -name "species" -type "enumeration" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Species type of the animal" \
      :restriction -base "enumeration" \
      < \
        :dictionary "nig.pssd.animal.species" \
      > \
    > \
    :element -name "body-part" -type "enumeration" -index "true" -min-occurs "0" \
    < \
      :description "Body part of the animal" \
      :restriction -base "enumeration" \
      < \
        :dictionary "nig.pssd.animal.bodypart" \
      > \
      :attribute -name "sidedness" -type "boolean" -min-occurs "0" \
      < \
        :description "If the body part comes from the left or right (your convention for orientation) side you can specify here.  Don't supply to leave unspecified." \
      > \
    > \
    :element -name "gender" -type "enumeration" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Gender of the subject" \
      :restriction -base "enumeration" \
      < \
        :value "male" \
        :value "female" \
        :value "other" \
        :value "unknown" \
      > \
    > \
   :element -name "birthDate" -type "date" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Birth date of the subject" \
      :restriction -base "date" \
       < \
         :time "false" \
       > \
    > \
    :element -name "deceased" -type "boolean" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Subject is deceased (cadaver)" \
    > \
    :element -name "deathDate" -type "date" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Death date of the subject" \
      :restriction -base "date" \
      < \
        :time "false" \
      > \
    > \
    :element -name "age-at-death" -type "integer" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Age of subject (days or weeks) at time of death (intended for non-human subjects)." \
      :restriction -base "integer" \
      < \
        :minimum "0" \
      > \
      :attribute -name "units" -type "enumeration" -min-occurs "0" \
      < \
        :restriction -base "enumeration" \
        < \
          :value "days" \
          :value "weeks" \
        > \
      > \
    > \
    :element -name "weight-at-death" -type "float" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Weight of subject (g or Kg) at time of death (intended for non-human subjects." \
      :restriction -base "float" \
      < \
        :minimum "0" \
      > \
      :attribute -name "units" -type "enumeration" -min-occurs "0" \
      < \
        :restriction -base "enumeration" \
        < \
          :value "g" \
          :value "Kg" \
        > \
      > \
    > \
   >


}

#=============================================================================
proc createDocType_pssd_animal_disease {} {

asset.doc.type.update :create yes :type nig-daris:pssd-animal-disease \
  :label "nig-daris:pssd-animal-disease" \
  :description "Document type for animal subject (Humans included) disease" \
  :definition < \
    :element -name "disease" -type "enumeration" -index "true" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Disease pathology of the subject" \
      :restriction -base "enumeration" \
      < \
        :dictionary "nig.pssd.subject.pathology" \
      > \
    > \
    :element -name "disease-state" -type "document" -index "true" -min-occurs "0" \
    < \
      :element -name "state" -type "enumeration" -index "true" -min-occurs "0" -max-occurs "1" \
      < \
        :description "State of the disease at the specified time" \
        :restriction -base "enumeration" \
        < \
          :value "pre-symptomatic" \
          :value "symptomatic" \
          :value "asymptomatic" \
        > \
      > \
      :element -name "time" -type "document" -index "true" -min-occurs "0" -max-occurs "1" \
      < \
        :element -name "date" -type "date" -index "true" -min-occurs "0" -max-occurs "1" \
        < \
          :description "Date when current disease state was set" \
          :restriction -base "date" \
          < \
            :time "false" \
          > \
        > \
        :element -name "time-point" -type "integer" -index "true" -min-occurs "0" -max-occurs "1" \
        < \
          :description "Time-point (0 is baseline) when current disease state was set" \
        > \
      > \
    > \
   >
	}

#=============================================================================
# Generally, human subjects are re-used and so these meta-data should be placed
# on the R-Subject object
# Should be paired with nig-daris:pssd-subject
proc createDocType_pssd_human_subject {} {

	asset.doc.type.update \
		:create true :type nig-daris:pssd-human-subject \
		:description "Document type for a Human subject" \
		:label "Human Subject" \
		:definition < \
		:element -name handedness -min-occurs 0 -max-occurs 1 -type enumeration \
		-enumerated-values left,right,ambidextrous,unknown -index true \
		-case-sensitive false < \
		:description "Handedness of the subject" \
		> \
		:element -name height  -min-occurs 0 -max-occurs 1 \
		-type float -min 0.0 -index true  < \
		:attribute -type enumeration -name units -enumerated-values "m" -min-occurs 0  \
		:description "Height of subject (m)" \
		> \
		:element -name smoking  -min-occurs 0 -max-occurs 1 \
		-type enumeration -enumerated-values never,ex,social,current,unknown \
		-index true -case-sensitive false < \
		:description "Smoking habits." \
		> \
		:element -name alcohol -min-occurs 0 -max-occurs 1  -type float -min 0.0 -index true  < \
		:attribute -type enumeration -name units -enumerated-values "unit/day" -min-occurs 0  \
		:description "Average consumption of alcohol (unit/day). One unit = 10ml or 8g" \
		> \
		>

}

#=============================================================================
# Generally,     human subjects are     re-used and so these meta-data should be
# placed on the Identity  object
# Generally, non-human subjects are not re-used and so these meta-data should be
# placed on the Subject object
# This Document Type exists because the content does not need to be protected
# (unlike nig-daris:pssd-human-identity)
proc createDocType_pssd_identity {} {

	asset.doc.type.update \
		:create true :type nig-daris:pssd-identity \
		:description "Document type for subject identity" \
		:label "External Subject Identifier" \
		:definition < \
		:element -name id -min-occurs 0 -max-occurs infinity  -type string -index true  < \
		:description "Unique identifier for the subject allocated by some other authority for cross-referencing" \
		:attribute -name type -type enumeration -min-occurs 0 < \
		:restriction -base enumeration < \
		:value "Florey Animal Service" \
		:value "Florey Small Animal MR Facility" \
		:value "Florey Integrative Neuroscience Facility" \
		:value "Launceston General Hospital" \
		:value "Melbourne Brain Centre Imaging Unit" \
		:value "Melbourne Brain Centre Imaging Unit 7T" \
		:value "Royal Children's Hospital" \
		:value "Royal Hobart Hospital" \
		:value "Victorian Infant Brain Studies" \
		:value Other \
		> \
		> \
		> \
		>

}

#=============================================================================
# Generally, human subjects are  re-used and so these meta-data should be placed
# on the Identity object
proc createDocType_pssd_human_identity {} {

	asset.doc.type.update \
		:create true \
		:type nig-daris:pssd-human-identity \
		:label "Human Identification" \
		:description "Document type for human subject identity" \
		:definition < \
		:element -name prefix -type string -min-occurs 0 -max-occurs 1 -length 20 -label "Prefix" \
		:element -name first  -type string -min-occurs 1 -max-occurs 1 -length 40 -label "First" \
		:element -name middle -type string -min-occurs 0 -max-occurs 1 -length 100 -label "Middle" < \
		:description "If there are several 'middle' names then put them in this field" \
		> \
		:element -name last   -type string -min-occurs 1 -max-occurs 1 -length 40 -label "Last" \
		:element -name suffix -type string -min-occurs 0 -max-occurs 1 -length 20 -label "Suffix" \
		>

}

#=============================================================================
proc createDocType_pssd_human_name {} {

	asset.doc.type.update \
		:create true \
		:type nig-daris:pssd-human-name \
		:label "Human name" \
		:description "Document type for human name" \
		:definition < \
		:element -name prefix -type string -min-occurs 0 -max-occurs 1 -length 20 -label "Prefix (e.g. Dr.)" \
		:element -name first  -type string -min-occurs 1 -max-occurs 1 -length 40 -label "First" \
		:element -name middle -type string -min-occurs 0 -max-occurs 1 -length 100 -label "Middle" < \
		:description "If there are several 'middle' names then put them in this field" \
		> \
		:element -name last   -type string -min-occurs 1 -max-occurs 1 -length 40 -label "Last" \
		:element -name suffix -type string -min-occurs 0 -max-occurs 1 -length 20 -label "Suffix" \
		>

}

#=============================================================================
proc createDocType_pssd_human_education {} {

	asset.doc.type.update \
		:create true \
		:type nig-daris:pssd-human-education \
		:label "Human Education" \
		:description "Document type for human education" \
		:definition < \
		:element -name primary  -min-occurs 0 -max-occurs 1 \
		-type integer -min 0 -index true  < \
		:attribute -type enumeration -name units -enumerated-values "years" -min-occurs 0  \
		:description "Years of primary education" \
		> \
		:element -name secondary  -min-occurs 0 -max-occurs 1 \
		-type integer -min 0 -index true  < \
		:attribute -type enumeration -name units -enumerated-values "years" -min-occurs 0  \
		:description "Years of secondary education" \
		> \
		:element -name tertiary  -min-occurs 0 -max-occurs 1 \
		-type integer -min 0 -index true  < \
		:attribute -type enumeration -name units -enumerated-values "years" -min-occurs 0  \
		:description "Years of tertiary education" \
		> \
		:element -name higher  -min-occurs 0 -max-occurs 1 \
		-type integer -min 0 -index true  < \
		:attribute -type enumeration -name units -enumerated-values "years" -min-occurs 0  \
		:description "Years of higher (e.g. Masters, PhD)  education" \
		> \
		>

}

#=============================================================================
proc createDocType_pssd_human_contact {} {

	asset.doc.type.update \
		:create true \
		:type nig-daris:pssd-human-contact \
		:label "Human Contact" \
		:description "Document type for human contacts" \
		:definition < \
		:element -name doctor -type document -index true -min-occurs 0 -max-occurs infinity < \
		:description "Referring Doctor or other health-care professional" \
		:reference -name name -type document < :value "nig-daris:pssd-human-name" > \
		>\
		>

}

#=============================================================================
# TODO: Study-related Document Types: nig-daris:pssd-study
#proc createDocType_pssd_study {} {
#	asset.doc.type.update \
		:create true \
		:type nig-daris:pssd-study \
		:description "Generic document type for Study" \
		:label "Study" \
		:definition < \
		> \
		>
#}

#=============================================================================
proc createDocType_pssd_PET_study {} {

	asset.doc.type.update \
		:create true \
		:type nig-daris:pssd-PET-study \
		:description "Document type for PET Study" \
		:label "PET Study" \
		:definition < \
		:element -name tracer -min-occurs 1 -max-occurs 1 \
		-type enumeration  -dictionary nig.PET.tracer -index true -case-sensitive false < \
		:description "Tracer of the PET acquisition" \
		> \
		>

}

#============================================================================#
proc createPSSDCoreDocTypes { } {

	createDocType_pssd_subject
	createDocType_pssd_animal_subject
	createDocType_pssd_animal_disease
	createDocType_pssd_human_subject
	createDocType_pssd_identity
	createDocType_pssd_human_identity
	createDocType_pssd_human_name
	createDocType_pssd_human_education
	createDocType_pssd_human_contact
#	createDocType_pssd_study
	createDocType_pssd_PET_study

}

#============================================================================#
proc destroyPSSDCoreDocTypes { } {

	set doctypes {  nig-daris:pssd-subject nig-daris:pssd-animal-subject nig-daris:pssd-animal-disease \
					nig-daris:pssd-human-subject nig-daris:pssd-identity nig-daris:pssd-human-identity nig-daris:pssd-human-name \
					nig-daris:pssd-human-education nig-daris:pssd-human-contact nig-daris:pssd-study nig-daris:pssd-PET-study }

	foreach doctype $doctypes {
        destroyDocType $doctype "true"
	}

}

#============================================================================#
#                                                                            #
#============================================================================#
createPSSDCoreDocTypes
