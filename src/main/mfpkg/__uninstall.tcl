# ============================================================================
# Include the util functions
# ============================================================================
source utils.tcl

# ============================================================================
# Uninstall Plugins
# ============================================================================
set plugin_label      [string toupper PACKAGE_$package]
set plugin_namespace  mflux/plugins/daris-nig
set plugin_jar        daris-nig-plugin.jar
set plugin_path       $plugin_namespace/$plugin_jar
set module_class      nig.mf.plugin.pssd.ni.NIGPSSDPluginModule
unloadPlugin $plugin_path $module_class
