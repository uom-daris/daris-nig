if { [ xvalue exists [asset.doc.namespace.exists :namespace "nig-daris"]] == "false" } {
  asset.doc.namespace.update :create true :namespace nig-daris :description "Metadata namespace for the Neuroimaging Group (NIG) DaRIS/PSSD data model framework document types"
}

# Document: nig-daris:pssd-mbic-fmp-check [version 1]
#
asset.doc.type.update :create yes :type nig-daris:pssd-mbic-fmp-check \
  :label "nig-daris:pssd-mbic-fmp-check" \
  :description "Check DICOM against FileMakerPro variables." \
  :definition < \
    :element -name "pet" -type "boolean" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Has a PET DataSet been checked (by nig.pssd.mbic.petvar.check)." \
    > \
    :element -name "ct" -type "boolean" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Has a CT DataSet been checked ((by nig.pssd.mbic.petvar.check). " \
    > \
   :element -name "dose" -type "boolean" -min-occurs "0" -max-occurs "1" \
    < \
      :description "Has the SR DataSet been processed (by nig.pssd.mbic.dose.upload). " \
    > \
   >


# Document nig-daris:ms-gait-subject
asset.doc.type.update :create yes :type nig-daris:ms-gait-subject \
    :label "nig-daris:ms-gait-subject" \
    :description "Document for MS Gait subject information." \
    :definition < \
        :element -name address -label "Address" -type string -encrypt true -min-occurs 0 -max-occurs 1 < \
            :description "The address of the subject." > \
        :element -name phone -label "Phone Number" -type string -encrypt true -min-occurs 0 -max-occurs 3  < \
            :description "The subject's phone number." > \
        :element -name imed -label "IMED Number" -type string -encrypt true -min-occurs 0 -max-occurs 1 < \
            :description "The subject's IMED number." > \
        :element -name ur -label "UR Number" -type string -encrypt true -min-occurs 0 -max-occurs 1 < \
            :description "The subject's UR number." > \
        :element -name msg-code -label "MSG Code" -type string -encrypt true -min-occurs 0 -max-occurs 1 < \
            :description "The subject's MSG code (MRI)." > \
        :element -name ms-code -label "MS Code" -type string -encrypt true -min-occurs 0 -max-occurs 1 < \
            :description "The subject's MS code (Gait Lab)." > >

