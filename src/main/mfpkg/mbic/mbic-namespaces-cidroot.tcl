# This is for the MBC Imaging Unit archive (DICOM and raw data)
# Store must pre-exist (generally configured after Mediaflux server install
# and before packages are installed
#
# PET/CT 
# This store is used for the PSSD and fall through namespace
set mbc_store MBIC-dicom

# If the store does not exist, this function will do nothing
# If the namespace pre-exists, it does nothing
# This is the DICOM archive used as a fall through if uploads to DaRIS fail
# This namespace name is coupled to the role perms script (should be
# pulled out into an argument for both)
createNamespace "MBIC-PETCT-dicom" "MBC Imaging Unit PET/CT Fall Through Namespace" $mbc_store
createNamespace "MBIC-MR-dicom" "MBC Imaging Unit MR Fall Through Namespace" $mbc_store

# This is the DaRIS archive 
set mbc_pssd_namespace "mbic.pssd"
createNamespace $mbc_pssd_namespace "MBC Imaging Unit DaRIS Archive" $mbc_store

# Add to pssd dictionary listing namespaces available for Project creation
# presented in Portal  GUI
addDictionaryEntry "daris:pssd.project.asset.namespaces" $mbc_pssd_namespace

# Create the cid root to be used for MBIC Projects and add to PSSD dictionary
# that is used to present known list in Portal GUI
set mbc_cid_root mbic.project
citeable.named.id.create :name $mbc_cid_root
addDictionaryEntry "daris:pssd.project.cid.rootnames" $mbc_cid_root

# Create the cid root to be used for the MBIC Archive Project(s) and add to PSSD dictionary
# that is used to present known list in Portal GUI
set mbc_archive_cid_root mbic.project.archive
citeable.named.id.create :name $mbc_archive_cid_root
addDictionaryEntry "daris:pssd.project.cid.rootnames" $mbc_archive_cid_root
