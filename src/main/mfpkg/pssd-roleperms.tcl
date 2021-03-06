# Standard Document Types
set pssd_doc_perms { { document nig-daris:pssd-subject ACCESS } \
						{ document nig-daris:pssd-subject PUBLISH } \
						{ document nig-daris:pssd-subject-exclusion ACCESS } \
						{ document nig-daris:pssd-subject-exclusion PUBLISH } \
						{ document nig-daris:pssd-animal-subject ACCESS } \
						{ document nig-daris:pssd-animal-subject PUBLISH } \
						{ document nig-daris:pssd-animal-disease ACCESS } \
						{ document nig-daris:pssd-animal-disease PUBLISH } \
						{ document nig-daris:pssd-human-subject ACCESS } \
						{ document nig-daris:pssd-human-subject PUBLISH } \
						{ document nig-daris:pssd-human-education ACCESS } \
						{ document nig-daris:pssd-human-education PUBLISH } \
						{ document nig-daris:pssd-identity ACCESS } \
						{ document nig-daris:pssd-identity PUBLISH } \
						{ document nig-daris:pssd-human-identity ACCESS } \
						{ document nig-daris:pssd-human-identity PUBLISH } \
						{ document nig-daris:pssd-animal-genetics ACCESS } \
						{ document nig-daris:pssd-animal-genetics PUBLISH } \
						{ document nig-daris:pssd-amrif-subject ACCESS } \
						{ document nig-daris:pssd-amrif-subject PUBLISH } \
						{ document nig-daris:pssd-animal-modified-genetics ACCESS } \
						{ document nig-daris:pssd-animal-modified-genetics PUBLISH } \
						{ document nig-daris:pssd-anaesthetic ACCESS } \
						{ document nig-daris:pssd-anaesthetic PUBLISH } \
						{ document nig-daris:pssd-recovery ACCESS } \
						{ document nig-daris:pssd-recovery PUBLISH } \
						{ document nig-daris:pssd-animal-kill ACCESS } \
						{ document nig-daris:pssd-animal-kill PUBLISH }
						{ document nig-daris:pssd-mineral-subject ACCESS } \
						{ document nig-daris:pssd-mineral-subject PUBLISH } \
						{ document nig-daris:pssd-artificial-subject ACCESS } \
						{ document nig-daris:pssd-artificial-subject PUBLISH } \
					}
					
# Image HD document types
set pssd_ImageHD_doc_perms { { document nig-daris:pssd-time-point ACCESS } \
			     { document nig-daris:pssd-time-point PUBLISH } \
			     { document nig-daris:pssd-ImageHD-combined ACCESS } \
			     { document nig-daris:pssd-ImageHD-combined PUBLISH } }

# EAE document types
set pssd_EAE_doc_perms { { document nig-daris:pssd-EAE-perfusion ACCESS } \
						{ document nig-daris:pssd-EAE-perfusion PUBLISH } \
						{ document nig-daris:pssd-EAE-stain ACCESS } \
						{ document nig-daris:pssd-EAE-stain PUBLISH } \
						{ document nig-daris:pssd-EAE-optic-nerve-removal ACCESS } \
						{ document nig-daris:pssd-EAE-optic-nerve-removal PUBLISH } \
						{ document nig-daris:pssd-EAE-optic-nerve-section ACCESS } \
						{ document nig-daris:pssd-EAE-optic-nerve-section PUBLISH } \
						{ document nig-daris:pssd-EAE-microscopy ACCESS } \
						{ document nig-daris:pssd-EAE-microscopy PUBLISH } }

set pssd_svc_perms { { service nig.pssd.* ACCESS } \
		     { service nig.pssd.* MODIFY } \
                     { service server.database.describe ACCESS } }


#set model_user_role               pssd.model.user
set project_creator_role          pssd.project.create   
set subject_creator_role          pssd.subject.create
set r_subject_admin_role          pssd.r-subject.admin
set r_subject_guest_role          pssd.r-subject.guest
set object_admin_role             pssd.object.admin
set object_guest_role             pssd.object.guest

# User
set domain_model_user_role        nig.pssd.model.user
createRole     $domain_model_user_role

# Grant end users the right to access the nig-daris document namespace
actor.grant :name  $domain_model_user_role :type role :perm < :resource -type document:namespace nig-daris :access ACCESS >

#Grant document and service perms
grantRolePerms $domain_model_user_role $pssd_doc_perms
grantRolePerms $domain_model_user_role $pssd_EAE_doc_perms
grantRolePerms $domain_model_user_role $pssd_ImageHD_doc_perms
grantRolePerms $domain_model_user_role $pssd_svc_perms



# ============================================================================
# Role: nig.pssd.administrator 
#
# Holders of this role should be able to undertake nig-pssd admin activities
# without the full power of system:administrator.  Admin services
# require permission ADMINISTER to operate. Also grants the
# daris:pssd.administrator (which holds and daris:essentials.administrator) roles
# ============================================================================
createRole nig.pssd.administrator
actor.grant :name nig.pssd.administrator :type role  \
      :role -type role  $domain_model_user_role \
	  :role -type role daris:pssd.administrator \
      
# These services need ADMINISTER to be able to execute
actor.grant :type role :name nig.pssd.administrator :perm < :resource -type service nig.pssd.* :access * >

##########################################################################
# These specialized services grant roles to other roles and users
# They need to have system-administrator role to do this
grantRole plugin:service nig.pssd.user.create system-administrator
grantRole plugin:service nig.pssd.dicom.user.create system-administrator


# DICOM
set dicom_ingest_doc_perms {                    { document nig-daris:pssd-subject ACCESS } \
                                                { document nig-daris:pssd-subject PUBLISH } \
						{ document nig-daris:pssd-subject-exclusion ACCESS } \
						{ document nig-daris:pssd-animal-subject ACCESS } \
						{ document nig-daris:pssd-animal-subject PUBLISH } \
						{ document nig-daris:pssd-animal-disease ACCESS } \
						{ document nig-daris:pssd-animal-disease PUBLISH } \
						{ document nig-daris:pssd-human-subject ACCESS } \
						{ document nig-daris:pssd-human-subject PUBLISH } \
						{ document nig-daris:pssd-human-education ACCESS } \
						{ document nig-daris:pssd-human-education PUBLISH } \
						{ document nig-daris:pssd-identity ACCESS } \
						{ document nig-daris:pssd-identity PUBLISH } \
						{ document nig-daris:pssd-human-identity ACCESS } \
						{ document nig-daris:pssd-human-identity PUBLISH } \
						{ document nig-daris:pssd-animal-genetics ACCESS } \
						{ document nig-daris:pssd-animal-genetics PUBLISH } \
						{ document nig-daris:pssd-animal-modified-genetics ACCESS } \
						{ document nig-daris:pssd-animal-modified-genetics PUBLISH } \
						{ document nig-daris:pssd-anaesthetic ACCESS } \
						{ document nig-daris:pssd-recovery ACCESS } \
						{ document nig-daris:pssd-animal-kill ACCESS } \
						{ document nig-daris:pssd-time-point ACCESS } \
						{ document nig-daris:pssd-ImageHD-combined ACCESS } }
set dicom_ingest_service_perms { { service nig.pssd.subject.meta.set MODIFY } }
#
set domain_dicom_ingest_role      nig.pssd.dicom-ingest
createRole     $domain_dicom_ingest_role
actor.grant :name $domain_dicom_ingest_role :type role :perm < :resource -type document:namespace nig-daris :access ACCESS >

# allow executing nig.pssd.mbic.petvar.check nig.pssd.mbic.dose.upload nig.pssd.mbic.fmp.uploads services
actor.grant :name $domain_dicom_ingest_role :type role :perm < :resource -type service nig.pssd.mbic.* :access * >
actor.grant :name $domain_dicom_ingest_role :type role :perm < :resource -type service daris.* :access ACCESS >
actor.grant :name $domain_dicom_ingest_role :type role :perm < :resource -type service om.pssd.* :access ACCESS >

grantRolePerms $domain_dicom_ingest_role $dicom_ingest_doc_perms
grantRolePerms $domain_dicom_ingest_role $dicom_ingest_service_perms
