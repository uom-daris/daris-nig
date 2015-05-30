#=============================================================================
proc createDict_pssd_phantom { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.phantom]] == "false" } {
		dictionary.create :name nig.pssd.phantom :description "MR Phantoms" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.phantom  Siemens-10606820  "1H spectroscopy sphere"
	addDictionaryEntry  nig.pssd.phantom  Siemens-10606821  "Yellow PDMS oil sphere"
    addDictionaryEntry  nig.pssd.phantom  Siemens-07721710  "blue Marcol oil sphere"
    addDictionaryEntry  nig.pssd.phantom  QED-2000913       "bottle with 31P"
    addDictionaryEntry  nig.pssd.phantom  QED-2000914       "bottle with 13C"
    addDictionaryEntry  nig.pssd.phantom  QED-2000915       "bottle with 19F"
    addDictionaryEntry  nig.pssd.phantom  QED-2000916       "bottle with 23Na"
}


#============================================================================#
proc createUpdatePSSDDicts-artificial { } {

	createDict_pssd_phantom
}

#============================================================================#
proc destroyPSSDDicts-artificial { } {

	set dicts { nig.pssd.phantom }
	foreach dict $dicts {
		if { [xvalue exists [dictionary.exists :name $dict]] == "true" } {
			dictionary.destroy :name $dict
		}
	}
}