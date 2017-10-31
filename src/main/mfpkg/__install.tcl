# Supply arguments with 
#  package.install :arg -name <arg name> <value>
#
# Arguments:
#     studyTypes - If set to false, does not add the nig-pssd Study type definitions.
#                  Defaults to true.
#           model - Set to false to not make any changes to the object model such as what meta-data
#                   are registered with the data model.  Defaults to true.
#           fillIn - Set to true to fill in CID space for Methods when creating
#           action - If Method pre-exists, action = 0 (do nothing), 1 (replace), 2 (create new)
#           mbic   - If true (defaults to false), setup MBC Imaging Unit asset namespace
#                    (store must exist) and roles

source old-release-cleanup.tcl

# ============================================================================
# Include the utils.tcl functions
# ============================================================================
source utils.tcl

# DaRIS underwent a namespaces migration (stable-2-27).   Document, dictionary and role namespaces
# were all migrated out of the global namespace.   The migration must have been executed
# before installing this version (unless it's a fresh install - no PSSD objects).
if { [isDaRISInstalled] == "true" } {
   checkAppProperty daris daris-namespaces-migrate-1 "DaRIS namespaces migration has not been done. You must undertake this migration first by installing stable-2-27 and undertaking the migration."
}

#============================================================================
# Create dictionaries
#
# Note: it is created first because services may, when being reloaded, 
#       instantiate classes which specify dictionaries
#============================================================================
source pssd-dictionaries.tcl
createUpdatePSSDDicts

source pssd-dictionaries-artificial.tcl
createUpdatePSSDDicts-artificial


#============================================================================
# Add our Study Types. The command-line arguments allows you to choose to
# not add our study types, so other sites can fully define their own.
#
# Really just a dictionary, but we keep it logically separate
#============================================================================
set addStudyTypes 1
if { [info exists studyTypes ] } {
    if { $studyTypes == "false" } {
	set addStudyTypes 0
    }
}
if { $addStudyTypes == 1 } {
   source pssd-studytypes.tcl
   create_PSSD_StudyTypes
}

# ============================================================================
# Install plugins
# ============================================================================
set plugin_label      [string toupper PACKAGE_$package]
set plugin_namespace  mflux/plugins/daris-nig
set plugin_zip        daris-nig-plugin.zip
set plugin_jar        daris-nig-plugin.jar
set module_class      nig.mf.plugin.pssd.ni.NIGPSSDPluginModule
# none of these previously to adding FMP-based functionality
set plugin_libs       { daris-commons.jar mbciu.jar }
loadPlugin $plugin_namespace $plugin_zip $plugin_jar $module_class $plugin_label $plugin_libs
srefresh


# Meta-data namespace

if { [xvalue exists [asset.doc.namespace.exists :namespace "nig-daris"]] == "false" } {
   asset.doc.namespace.create \
      :description "Metadata namespace for the Neuroimaging Group (NIG) DaRIS/PSSD data model framework document types" \
      :namespace nig-daris
}
 
#=============================================================================
# Creates core doc types
#=============================================================================
source pssd-doctypes-core.tcl

#=============================================================================
# Create generic doc types in method
#=============================================================================
source pssd-doctypes-generic.tcl

#=============================================================================
# Create artificial doc types in method
#=============================================================================
source pssd-doctypes-artificial.tcl

#=============================================================================
# Create ImageHD doc types
#=============================================================================
source pssd-doctypes-ImageHD.tcl

#=============================================================================
# Create EAE doc types
#=============================================================================
source pssd-doctypes-EAE.tcl

# Method fill-in
set fillInMethods 1
if { [info exists fillIn ] } {
    if { $fillIn == "false" } {
	  set fillInMethods 0
    }
}

# Method action
set methodAction 0
if { [info exists action ] } {
  set methodAction $action
}

#=============================================================================
# Create AnimalMRISimple method
#=============================================================================
source pssd-method-AnimalMRISimple.tcl
createMethod_animal_mri_simple $methodAction $fillInMethods

#=============================================================================
# Create AnimalMRISimple_Private method
#=============================================================================
source pssd-method-AnimalMRISimple_Private.tcl
createMethod_animal_mri_simple_private $methodAction $fillInMethods

#=============================================================================
# Create HumanMRISimple method
# This Method is now deprecated in favour of the NoRsubject flavour
#=============================================================================
#source pssd-method-HumanMRISimple.tcl
#createMethod_human_mri_simple $methodAction $fillInMethods

#=============================================================================
# Create HumanMRISimple_NoRSubject method
#=============================================================================
source pssd-method-HumanMRISimple_NoRSubject.tcl
createMethod_human_mri_simple_no_rs $methodAction $fillInMethods

#=============================================================================
# Create ImageHD method
#=============================================================================
source pssd-method-ImageHD.tcl
create_ImageHD_method $methodAction $fillInMethods

#=============================================================================
# Create EAE method
#=============================================================================
source pssd-method-EAE.tcl
create_EAE_Method $methodAction $fillInMethods


#=============================================================================
# Create Multi-mode testing method
#=============================================================================
source pssd-method-multi-mode.tcl
create_multi-mode-method $methodAction $fillInMethods

#=============================================================================
# Register doc types with the data model
#=============================================================================
set addModel 1
if { [info exists model] } {
    if { $model == "false" } {
	set addModel 0
    }
}
if { $addModel == 1 } {
   source pssd-register-doctypes.tcl
}

#=============================================================================
# Register role-members
#=============================================================================
source pssd-register-rolemembers.tcl

#=============================================================================
# Set up roles & permissions
#=============================================================================
source pssd-roleperms.tcl
source pssd-service-perms.tcl


####################
# MBC Imaging Unit
####################
if { [info exists mbic ] } {
    if { $mbic == "true" } {
        # Namespaces/cid roots
	    source mbic/mbic-namespaces-cidroot.tcl
       
        # Doc Types
        source mbic/mbic-pssd-doctypes.tcl
       
        # Permissions
        source mbic/mbic-roleperms.tcl      
       
        # Triggers
        source mbic/triggers-install.tcl
       
        # Methods
        source mbic/mbic-pssd-method-human.tcl
        create-MBIC-Human-method $methodAction $fillInMethods
	    #      
        source mbic/mbic-pssd-method-animal.tcl
        create-MBIC-Animal-method $methodAction $fillInMethods
        #
        source mbic/mbic-pssd-method-mineral.tcl
        create-MBIC-Mineral-method $methodAction $fillInMethods
        #
        source mbic/mbic-pssd-method-QA.tcl
        create-MBIC-QA-method $methodAction $fillInMethods
        #
        source mbic/mbic-pssd-method-artificial.tcl
        create-MBIC-Artificial-method $methodAction $fillInMethods
        #
        source mbic/mbic-pssd-method-generic.tcl
        create-MBIC-Generic-method  $methodAction $fillInMethods
        #
        source mbic/mbic-pssd-method-MR-archive.tcl
        create-MBIC-MR-Archive-method $methodAction $fillInMethods
        #
        source mbic/mbic-pssd-method-ms-gait.tcl
        create-MBIC-MS-GAIT-method $methodAction $fillInMethods

    }
}
