# Destroy the TIMID trigger
set ns mbic.pssd
 if { [xvalue namespace/exists [asset.trigger.on.exists :name TIMID :namespace $ns]] == "true" } {
   asset.trigger.destroy :name TIMID :namespace $ns
   asset.query :where name='trigger-mbic-timid.tcl' :action pipe :service -name asset.hard.destroy
 }
