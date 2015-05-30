proc setNameSpaceACL { ns adminRole uploadRole } {
						
# Set ACLs on the DICOM asset namespace for Admin and Upload
asset.namespace.acl.set :namespace $ns \
   :acl < :actor -type role $adminRole \
      :access < :asset create :asset access :asset modify \
                :asset destroy :asset-content access :asset-content modify \
                :namespace administer :namespace create :namespace access :namespace destroy > > \
   :acl < :actor -type role $uploadRole \
       :access < :asset create :asset access :asset modify \
       :asset destroy :asset-content access :asset-content modify :namespace access > >
  
}


####
# MBC Imaging Unit
# THese roles are for managing the Melbourne Brain Centre Imaging Unit archive

# DocTypes

set pssd_doc_perms { { document nig-daris:pssd-mbic-fmp-check ACCESS } \
						{ document nig-daris:pssd-mbic-fmp-check PUBLISH } }

# User
set domain_model_user_role        nig.pssd.model.user

#Grant document  perms
grantRolePerms $domain_model_user_role $pssd_doc_perms


# DICOM
set dicom_model_user_role        nig.pssd.dicom-ingest

#Grant document  perms
grantRolePerms $dicom_model_user_role $pssd_doc_perms



#######################
# PET/CT operations	
#######################			
# Apply restrictions to the MBIC DICOM  Namespace so that only the admin & upload roles have access
#
set petct_dicom_admin_role MBIC-PETCT-DICOM-Admin
createRole $petct_dicom_admin_role
#
set petct_dicom_upload_role MBIC-PETCT-DICOM-Upload
createRole $petct_dicom_upload_role
# Set
setNameSpaceACL "MBIC-PETCT-dicom" $petct_dicom_admin_role $petct_dicom_upload_role

# Grant system manager admin access  
actor.grant :type user :name system:manager :role -type role $petct_dicom_admin_role
   
# DICOM proxy users must be manually granted the Upload role
   
   
#######################
# MR operations	
#######################			
# Apply restrictions to the MBIC DICOM  Namespace so that only the admin & upload roles has access
#
set mr_dicom_admin_role MBIC-MR-DICOM-Admin
createRole $mr_dicom_admin_role
#
set mr_dicom_upload_role MBIC-MR-DICOM-Upload
createRole $mr_dicom_upload_role
# Set
setNameSpaceACL "MBIC-MR-dicom" $mr_dicom_admin_role $mr_dicom_upload_role

# Grant system manager admin access  
actor.grant :type user :name system:manager :role -type role $mr_dicom_admin_role

# DICOM proxy users must be manually granted the Upload role



    