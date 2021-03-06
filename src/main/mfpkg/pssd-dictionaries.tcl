# Populate Project keywords (dictionary part of PSSD package)

proc populate_research_keywords {} {
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term Neuroscience
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term MRI
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term PET/CT
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term PET
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term CT
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term 7T
   dictionary.entry.add :dictionary daris:pssd.research.keyword :term "Quality Assurance"   
}

#=============================================================================
proc populate_study_other_id_types {} {
   dictionary.entry.add :dictionary daris:pssd.study.other-id.types \
     :term "Melbourne Brain Centre Imaging Unit"  
   dictionary.entry.add :dictionary daris:pssd.study.other-id.types \
     :term "Melbourne Brain Centre Imaging Unit 7T Legacy"  
}
     
#=============================================================================
proc createDict_pssd_animal_bodypart { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.animal.bodypart]] == "false" } {
		dictionary.create :name nig.pssd.animal.bodypart :description "Animal Body Parts" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.animal.bodypart abdomen
	addDictionaryEntry  nig.pssd.animal.bodypart arm
  	addDictionaryEntry  nig.pssd.animal.bodypart "lower arm"
	addDictionaryEntry  nig.pssd.animal.bodypart "upper arm"
	addDictionaryEntry  nig.pssd.animal.bodypart chest
    addDictionaryEntry  nig.pssd.animal.bodypart fibula
    addDictionaryEntry  nig.pssd.animal.bodypart femur
	addDictionaryEntry  nig.pssd.animal.bodypart foot
	addDictionaryEntry  nig.pssd.animal.bodypart groin
	addDictionaryEntry  nig.pssd.animal.bodypart hand
	addDictionaryEntry  nig.pssd.animal.bodypart head
   	addDictionaryEntry  nig.pssd.animal.bodypart humerus
    addDictionaryEntry  nig.pssd.animal.bodypart knee
	addDictionaryEntry  nig.pssd.animal.bodypart leg
	addDictionaryEntry  nig.pssd.animal.bodypart "lower leg"
	addDictionaryEntry  nig.pssd.animal.bodypart "upper leg"
	addDictionaryEntry  nig.pssd.animal.bodypart neck
  	addDictionaryEntry  nig.pssd.animal.bodypart radius
	addDictionaryEntry  nig.pssd.animal.bodypart shoulder
   	addDictionaryEntry  nig.pssd.animal.bodypart skull  	
    addDictionaryEntry  nig.pssd.animal.bodypart thigh
    addDictionaryEntry  nig.pssd.animal.bodypart tibia
  	addDictionaryEntry  nig.pssd.animal.bodypart ulna
}

#=============================================================================
proc createDict_pssd_animal_species { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.animal.species]] == "false" } {
		dictionary.create :name nig.pssd.animal.species :description "Animal species" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.animal.species human
	addDictionaryEntry  nig.pssd.animal.species baboon
	addDictionaryEntry  nig.pssd.animal.species cat
	addDictionaryEntry  nig.pssd.animal.species "cane toad"
	addDictionaryEntry  nig.pssd.animal.species chicken
	addDictionaryEntry  nig.pssd.animal.species dog
    addDictionaryEntry  nig.pssd.animal.species echidna
	addDictionaryEntry  nig.pssd.animal.species goanna
	addDictionaryEntry  nig.pssd.animal.species guineapig
	addDictionaryEntry  nig.pssd.animal.species harrier
	addDictionaryEntry  nig.pssd.animal.species marmoset
	addDictionaryEntry  nig.pssd.animal.species mouse
	addDictionaryEntry  nig.pssd.animal.species monkey
	addDictionaryEntry  nig.pssd.animal.species pig
	addDictionaryEntry  nig.pssd.animal.species rat
	addDictionaryEntry  nig.pssd.animal.species rabbit
	addDictionaryEntry  nig.pssd.animal.species sheep
	addDictionaryEntry  nig.pssd.animal.species "tasmanian devil"
	addDictionaryEntry  nig.pssd.animal.species wallaby
}

#=============================================================================
proc createDict_pssd_human_relationship { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.human.relationship]] == "false" } {
		dictionary.create :name nig.pssd.human.relationship :description "Relationship of a human subject to another human" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.human.relationship Brother
	addDictionaryEntry  nig.pssd.human.relationship Sister
	addDictionaryEntry  nig.pssd.human.relationship Father
	addDictionaryEntry  nig.pssd.human.relationship Mother
	addDictionaryEntry  nig.pssd.human.relationship Husband
	addDictionaryEntry  nig.pssd.human.relationship Wife
	addDictionaryEntry  nig.pssd.human.relationship Spouse
	addDictionaryEntry  nig.pssd.human.relationship Partner
	addDictionaryEntry  nig.pssd.human.relationship Doctor
	addDictionaryEntry  nig.pssd.human.relationship "Health Care Professional"
	addDictionaryEntry  nig.pssd.human.relationship Friend
	addDictionaryEntry  nig.pssd.human.relationship Other

}

#=============================================================================
proc createDict_pssd_human_race { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.human.race]] == "false" } {
		dictionary.create :name nig.pssd.human.race :description "Human races"
	}

}

proc createDict_pssd_subject_pathology { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.subject.pathology]] == "false" } {
		dictionary.create :name nig.pssd.subject.pathology :description "Disease pathologies for Subjects" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.subject.pathology "Alzheimer's Disease"
	addDictionaryEntry  nig.pssd.subject.pathology "Amyotrophic Lateral Sclerosis"
	addDictionaryEntry  nig.pssd.subject.pathology "Experimental Autoimmune Encephalomyelitis (EAE)"
	addDictionaryEntry  nig.pssd.subject.pathology "Epilepsy"
	addDictionaryEntry  nig.pssd.subject.pathology "Freidrich Ataxia"
	addDictionaryEntry  nig.pssd.subject.pathology "Huntington's Disease"
	addDictionaryEntry  nig.pssd.subject.pathology "Multiple Sclerosis"
	addDictionaryEntry  nig.pssd.subject.pathology "Motor Neuron"
	addDictionaryEntry  nig.pssd.subject.pathology "Obesity"
	addDictionaryEntry  nig.pssd.subject.pathology "Optic Neuritis"
	addDictionaryEntry  nig.pssd.subject.pathology "Parkinson's Disease"
	addDictionaryEntry  nig.pssd.subject.pathology "Stroke"
	addDictionaryEntry  nig.pssd.subject.pathology "Other"
	addDictionaryEntry  nig.pssd.subject.pathology "Chronic Pain"
	addDictionaryEntry  nig.pssd.subject.pathology "None"

}

#=============================================================================
# Decided not to make species specific dictionaries at this point
proc createDict_pssd_animal_strains { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.animal.strains]] == "false" } {
		dictionary.create :name nig.pssd.animal.strains :description "Genetic strains of animals" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.animal.strains "C57Black" "Mouse strain"
	addDictionaryEntry  nig.pssd.animal.strains "129" "Mouse strain"
	addDictionaryEntry  nig.pssd.animal.strains "BALB/C" "Mouse strain"

}

#=============================================================================
proc createDict_pssd_animal_genes { } {

	if { [xvalue exists [dictionary.exists :name nig.pssd.animal.genes]] == "false" } {
		dictionary.create :name nig.pssd.animal.genes :description "Standard animal gene names" :case-sensitive true
	}
	addDictionaryEntry  nig.pssd.animal.genes "Leukemic Inhibitory Factor (LIF)"
	addDictionaryEntry  nig.pssd.animal.genes "Ciliary Neurotropic Factor (CNTF)"

}

#=============================================================================
proc createDict_PET_tracers { } {

	if { [xvalue exists [dictionary.exists :name nig.PET.tracer]] == "false" } {
		dictionary.create :name nig.PET.tracer :description "Standard PET imaging tacers" :case-sensitive true
	}
	addDictionaryEntry  nig.PET.tracer "11C-PIB" "Pittsburgh Compound B"
	addDictionaryEntry  nig.PET.tracer "18F-FDG" "18F-2-fluoro-deoxy-D-glucose"

}

#=============================================================================
proc createDict_ethics_organization { } {

	if { [xvalue exists [dictionary.exists :name nig.ethics.organization]] == "false" } {
		dictionary.create :name nig.ethics.organization :description "Organizations that supply ethics approval" :case-sensitive true
	}
	addDictionaryEntry  nig.ethics.organization "Alfred Medical Research and Education Precinct Animal Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Eye and Ear Hospital Human Research Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Florey Neuroscience Institutes Animal Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Howard Florey Institute Animal Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Melbourne Health Human Research Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Murdoch Childrens Research Institute Animal Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Monash University Human Research Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "Royal Children's Hospital Human Research Ethics Committee"
	addDictionaryEntry  nig.ethics.organization "University of Melbourne Human Research Ethics Committee"
}


#=============================================================================
proc createDict_funding_organization { } {

	if { [xvalue exists [dictionary.exists :name nig.funding.organization]] == "false" } {
		dictionary.create :name nig.funding.organization :description "Organizations that supply funding" :case-sensitive true
	}
	addDictionaryEntry  nig.funding.organization "Australian and New Zealand College of Anaesthetists"
	addDictionaryEntry  nig.funding.organization "Australian Research Council"
	addDictionaryEntry  nig.funding.organization "Derek Denton Endowment"
	addDictionaryEntry  nig.funding.organization "Florey Neuroscience Institutes"
	addDictionaryEntry  nig.funding.organization "Fred P Archer Charitable Trust"
	addDictionaryEntry  nig.funding.organization "National Health and Medical Research Council"
	addDictionaryEntry  nig.funding.organization "National Imaging Facility"
	addDictionaryEntry  nig.funding.organization "Other funding entity"							
	addDictionaryEntry  nig.funding.organization "University of Melbourne"
	addDictionaryEntry  nig.funding.organization "Other funding entity"							
}


#============================================================================#
proc createUpdatePSSDDicts { } {

	createDict_pssd_animal_species
	createDict_pssd_human_race
	createDict_pssd_human_relationship
	createDict_pssd_subject_pathology
	createDict_pssd_animal_strains
	createDict_pssd_animal_genes
	createDict_PET_tracers
	createDict_ethics_organization
	createDict_funding_organization
	createDict_pssd_animal_bodypart
	populate_research_keywords
	populate_study_other_id_types 
}

#============================================================================#
proc destroyPSSDDicts { } {

	set dicts { nig.pssd.animal.species nig.pssd.human.relationship nig.pssd.human.race nig.pssd.subject.pathology \
				nig.pssd.animal.strains	nig.pssd.animal.genes nig.PET.tracer nig.ethics.organization \
				nig.funding.organization nig.pssd.animal.bodypart \
		   }
	foreach dict $dicts {
		if { [xvalue exists [dictionary.exists :name $dict]] == "true" } {
			dictionary.destroy :name $dict
		}
	}
}

