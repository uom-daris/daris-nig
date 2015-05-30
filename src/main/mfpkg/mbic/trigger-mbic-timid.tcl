proc getProjectCID { cid } {
	set y [split $cid "."]
	set c1 [lindex $y 0]
	set c2 [lindex $y 1]
	set c3 [lindex $y 2]
	set projectCID "$c1.$c2.$c3"
	return $projectCID	
}


# Main

set asset_detail [asset.get :id $id]
set asset_model  [xvalue asset/model $asset_detail]
set timidProject "1.7.17"
set to williamsr@unimelb.edu.au

if { $asset_model == "om.pssd.subject" } {
	set cid [xvalue asset/cid   $asset_detail]
	set projectCID [getProjectCID $cid]		
	if { $projectCID == $timidProject } {
	   set sex [xvalue asset/meta/nig-daris:pssd-animal-subject/gender $asset_detail]
	   set dob [xvalue asset/meta/nig-daris:pssd-animal-subject/birthDate $asset_detail]
	   set first [xvalue asset/meta/nig-daris:pssd-human-identity/first $asset_detail]
	   set last [xvalue asset/meta/nig-daris:pssd-human-identity/last $asset_detail]
	   #	   
   	   set subject "New DaRIS subject $cid for the TIMID Project has been created" 
	   set body  "Subject :  $cid  \n Name   : $first $last \n Gender : $sex \n DOB     : $dob"

	   # Notify recpipient
	   mail.send :body $body :subject $subject :to $to	:async true
	}
}