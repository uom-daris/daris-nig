# remove the predeccessor (old release): nig-pssd
if { [xvalue exists [package.exists :package nig-pssd]] == "true" } {
    package.uninstall :package nig-pssd
}
if { [xvalue exists [asset.exists :id path=/mflux/plugins/nig-pssd-plugin.jar]] == "true" } {
    asset.hard.destroy :id path=/mflux/plugins/nig-pssd-plugin.jar
}
if { [xvalue exists [asset.exists :id path=/mflux/plugins/libs/nig-commons.jar]] == "true" } {
    asset.hard.destroy :id path=/mflux/plugins/libs/nig-commons.jar
}
if { [xvalue exists [asset.exists :id path=/mflux/plugins/libs/FMP-JDBC.jar]] == "true" } {
    asset.hard.destroy :id path=/mflux/plugins/libs/FMP-JDBC.jar
}
if { [xvalue exists [asset.exists :id path=/mflux/plugins/libs/MBC-FMP.jar]] == "true" } {
    asset.hard.destroy :id path=/mflux/plugins/libs/MBC-FMP.jar
}
if { [xvalue exists [asset.exists :id path=/mflux/plugins/libs/AIBL-Commons.jar]] == "true" } {
    asset.hard.destroy :id path=/mflux/plugins/libs/AIBL-Commons.jar
}
