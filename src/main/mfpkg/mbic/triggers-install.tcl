proc installTIMIDSubjectTrigger { } {

    set scriptPath    mbic/trigger-mbic-timid.tcl
    set script        trigger-mbic-timid.tcl
    set scriptNS      system/triggers
    set pssdNS        mbic.pssd    
    set label         [string toupper PACKAGE_NIG-PSSD]

    #
    # create the trigger script asset
    #
    asset.create :url archive:///$scriptPath \
	   :namespace -create yes $scriptNS \
	   :label -create yes $label :label PUBLISHED \
	   :name $script
    
    # create the trigger. The trigger script will work out whether to send the email or not
    asset.trigger.post.create :name TIMID :namespace -descend true $pssdNS :event create :script -type ref ${scriptNS}/${script}
}



### Main
source mbic/triggers-uninstall.tcl

# TIMID trigger.  Sends Rob an email when a new DaRIS Subject is created
installTIMIDSubjectTrigger
