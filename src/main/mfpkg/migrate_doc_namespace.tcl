# The migration process was done on daris-1 on 22May2014
# Create meta-data  namespace
if { [xvalue exists [asset.doc.namespace.exists :namespace "nig-daris"]] == "false" } {
   asset.doc.namespace.create :description "Metadata namespace for the Neuroimaging Group (NIG) DaRIS/PSSD data model framework document types" :namespace nig-daris
}
  
# Migrate doc types in standard assets.  Skips those that don't exist so can be restarted.
om.pssd.doctype.rename :templates true \
   :type < :old hfi.pssd.subject :new nig-daris:pssd-subject > \
   :type < :old hfi.pssd.animal.subject :new nig-daris:pssd-animal-subject > \
   :type < :old hfi.pssd.animal.disease :new nig-daris:pssd-animal-disease > \
   :type < :old hfi.pssd.human.subject :new nig-daris:pssd-human-subject > \
   :type < :old hfi.pssd.identity :new nig-daris:pssd-identity > \
   :type < :old hfi.pssd.human.identity :new nig-daris:pssd-human-identity > \
   :type < :old hfi.pssd.human.education :new nig-daris:pssd-human-education > \
   :type < :old hfi.pssd.human.relationship :new nig-daris:pssd-human-relationship > \
   :type < :old hfi.pssd.human.contact :new nig-daris:pssd-human-contact > \
   :type < :old hfi.pssd.human.name :new nig-daris:pssd-human-name > \
   :type < :old hfi.pssd.PET.study :new nig-daris:pssd-PET-study > \
   :type < :old hfi.pssd.EAE.perfusion :new nig-daris:pssd-EAE-perfusion > \
   :type < :old hfi.pssd.EAE.stain :new nig-daris:pssd-EAE-stain > \
   :type < :old hfi.pssd.EAE.optic-nerve.removal :new nig-daris:pssd-EAE-optic-nerve-removal > \
   :type < :old hfi.pssd.EAE.optic-nerve.section :new nig-daris:pssd-EAE-optic-nerve-section > \
   :type < :old hfi.pssd.EAE.microscopy :new nig-daris:pssd-EAE-microscopy > \
   :type < :old hfi.pssd.artificial.subject :new nig-daris:pssd-artificial-subject > \
   :type < :old hfi.pssd.mineral.subject :new nig-daris:pssd-mineral-subject > \
   :type < :old hfi.pssd.animal.genetics :new nig-daris:pssd-animal-genetics > \
   :type < :old hfi.pssd.animal.modified-genetics :new nig-daris:pssd-animal-modified-genetics > \
   :type < :old hfi.pssd.anaesthetic :new nig-daris:pssd-anaesthetic > \
   :type < :old hfi.pssd.recovery :new nig-daris:pssd-recovery > \
   :type < :old hfi.pssd.animal.kill :new nig-daris:pssd-animal-kill > \
   :type < :old hfi.pssd.amrif.subject :new nig-daris:pssd-amrif-subject > \
   :type < :old hfi.pssd.time-point :new nig-daris:pssd-time-point > \
   :type < :old hfi.pssd.subject.exclusion :new nig-daris:pssd-subject-exclusion > \
   :type < :old hfi.pssd.ImageHD.combined :new nig-daris:pssd-ImageHD-combined > \
   :type < :old hfi.pssd.AMBMC.brain.removal :new nig-daris:pssd-AMBMC-brain-removal > \
   :type < :old hfi.pssd.project :new nig-daris:pssd-project > \
   :type < :old hfi.pssd.study :new nig-daris:pssd-study > \
   :type < :old hfi.pssd.ethics :new nig-daris:pssd-ethics >