#============================================================================#
# string functions                                                           #
#============================================================================#

proc string_ends_with { str substr } {
    set len1 [string length $str]
    set len2 [string length $substr]
    set idx [string last $substr $str]
    if { $idx != -1 && [expr $len1 - $len2] == $idx } {
        return 1
    } else {
        return 0
    }
}

#============================================================================#
# dictionary functions                                                       #
#============================================================================#

#
# Creates a dictionary (and add its entries). It also creates dictionary 
# namespace if it is specifed.
#
proc create_dictionary { ns name description entries } {
    set dict $name
    if { ${ns}!="" } {
        dictionary.namespace.create \
            :description "Namespace: ${ns} for DaRIS dictionaries." \
            :namespace ${ns} \
            :ifexists ignore
        set dict ${ns}:${name}
    }
    if { [xvalue exists [dictionary.exists :name ${dict}]] == "false" } {
        dictionary.create :name ${dict} \
            :description ${description} \
            :case-sensitive true
    }
    foreach entry $entries {
        set term [lindex $entry 0]
        set defn [lindex $entry 1]
        if { [xvalue exists [dictionary.contains :dictionary ${dict} :term ${term}]] != "true" } {
            if { ${defn}!="" } {
                dictionary.entry.add :dictionary ${dict} :term ${term} :definition ${defn}
            } else {
                dictionary.entry.add :dictionary ${dict} :term ${term}
            }
        }
    }
}

#============================================================================#
# doc type functions                                                         #
#============================================================================#

#
# Destroys the doc type.
#
proc destroy_doc_type { type { force "true" } } {

    if { [xvalue exists [asset.doc.type.exists :type ${type}]] == "true" } {
        asset.doc.type.destroy :type ${type} :force ${force}
    }
}

#============================================================================#
# plugin module functions                                                    #
#============================================================================#

#
# Adds plugin module.
#
proc add_plugin_module { ns zip jar class label { libs { } } } {

    # import the plugin jar from the zip file to Mediaflux system. It will be 
    # an asset in the specified namespace with plugin jar file as content.
    asset.import :url archive:${zip} \
        :namespace -create yes ${ns} \
        :label -create yes ${label} :label PUBLISHED \
        :update true

    # build the command by appending arguments
    set args ":path ${ns}/${jar} :class ${class}"
    foreach lib $libs {
       set args "${args} :lib libs/${lib}"
    }

    # add plugin module
    if { [xvalue exists [plugin.module.exists :path ${ns}/${jar} :class ${class}]] == "false" } {
        plugin.module.add  ${args}
    }

    # Now that the plugins have been registered, we need to refresh the known
    # services with this session so that we can grant permissions for those 
    # plugins.
    system.service.reload

    # Make the (new) commands available to the enclosing shell.
    srefresh
}

#
# Removes plugin module.
#
proc remove_plugin_moudle { ns jar class { libs { } } } {

    # remove the plugin module
    if { [xvalue exists [plugin.module.exists :path ${ns}/${jar} :class ${class}]] == "true" } {
        plugin.module.remove :path ${ns}/${jar} :class ${class}
    }

    # destroy the plugin asset
    if { [xvalue exists [asset.exists :id path=${ns}/${jar}]] == "true" } {
        asset.hard.destroy :id path=${ns}/${jar}
    }

    # destroy the plugin libraries
    foreach lib ${libs} {
        asset.hard.destroy :id path=${ns}/${lib} 
    }

    system.service.reload

    srefresh
}

#============================================================================#
# actor functions                                                            #
#============================================================================#

#
# Grant the permissions to the given actor.
#
proc grant_actor_perms { actor_type actor_name perms } {
    foreach perm ${perms} {
        set res_type     [lindex ${perm} 0]
        set res_name     [lindex ${perm} 1]
        set res_access   [lindex ${perm} 2]
        actor.grant :type ${actor_type} :name ${actor_name} \
            :perm < :resource -type ${res_type} ${res_name} :access ${res_access} >
    }
}

#
# Grant the permissions to the given role.
#
proc grant_role_perms { role perms } {
    grant_actor_perms "role" ${role} ${perms}
}

#============================================================================#
# method functions                                                           #
#============================================================================#

#
# Gets the citeable id of the method with the given name.
# 
proc get_method_cid { method_name } {
    return [xvalue id [om.pssd.method.find :name ${method_name}]]
}

# 
# Set arguments for Method update service.
#
# Arguments:
#     id     - The citable ID of the Method, if it pre-exists
#     fillIn - Fill in citeable allocator space when creating methods
#     action - Controls what to do if the Method pre-exists (i.e. id is non-null)
#              If the Method does not pre-exist, a new one will always be made.
#          0 - means do nothing.
#          1 - means replace it with the new specification
#          2 - means make a new one anyway
# Returns: Returns 'quit' if no further action should be taken
#          Otherwise returns arguments to be handed to the 
#          om.pssd.method.for.subject.update service
#
proc set_method_update_args { id { action 0 } { fillIn 0 }} {
    set margs ""
    if  { $id != "" } {
        if { $action == "0" } {
            # Do nothing to pre-existing
            return "quit"
        } elseif { $action == "2" } {
            # Create new
            set margs ""
        } elseif { $action == "1" } {
            # Replace existing
            set margs ":replace 1 :id ${id}"
        } else {
            # Return do nothing
            return "quit"
        }
    } else {
        # Create new
    }
    if { $fillIn == "1" } {
       set margs  "${margs} :fillin true"
    } else {
       set margs  "${margs} :fillin false"
    }   
    return ${margs}
}

#
# Gets the default asset namespace for methods
#
proc get_asset_namespace_for_methods { } {
    set daris_asset_namespace [xvalue property [application.property.get :property -app daris daris.namespace.default]]
    if { ${daris_asset_namespace} == "pssd" || [string_ends_with ${daris_asset_namespace} "pssd"] == 1 } {
        return "${daris_asset_namespace}/methods"
    } else {
        return "${daris_asset_namespace}/pssd/methods"
    }
}