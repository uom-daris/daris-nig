package nig.mf.plugin.pssd.util.ni;

import arc.xml.*;
import nig.iio.metadata.DomainMetaData;
import nig.mf.Executor;
import nig.util.DateUtil;

import java.util.Collection;
import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * Supply the NIG domain-specific  project, subject and study meta-data to the DICOM server or Bruker client.
 * The framework is Data Model driven so that only the meta-data that could be attached is attached
 * 
 * The only reason we have functions for each object type is for clarity and because some
 * objects are handled slightly differently to others (add/merge/replace).  But the in reality,
 * the framework could hand in the object type and the test on object type be done internally
 * to this class. It does not matter much which way you do it.
 * 
 * The superclass, DomainMetaData sits in commons. In this way, specific packages like nig-pssd
 * can make use of the infrastructure but implement their own fully self-contained domain-specific
 * meta-data handler.
 * 
 * @author nebk
 *
 */
public class NIGDomainMetaData extends DomainMetaData {

	private static final String AMRIF_FACILITY = "aMRIF";
	private static final String RCH_FACILITY = "RCH";
	private static final String RHH_FACILITY = "RHH";
	
	// It's not good that we have two authorities. Ideally we'd have one
	// but we don't see that happening in the short term as 7T explores ARIN
	// and the PET/CT remains with FileMakerPro for now
	private static final String MBCIU_FACILITY_PETCT = "MBC-IU";
	private static final String MBCIU_FACILITY_7T = "MBC-IU-7T";

	private static final String LGH_FACILITY = "LGH";
	private static final String DATE_FORMAT = "dd-MMM-yyyy";

	// Constructor
	public NIGDomainMetaData () {
		//
	}



	/**
	 * 
	 * @param executor
	 * @param metaType SHould hold children elements "dicom" and/or "bruker" (but their children are irrelevant). This
	 * gives the context for which document types we are interested in.
	 * @param id
	 * @param objectType "project", "subject", "study"
	 * @param currentMeta The contents of xpath("asset/meta") after retrieval by asset.get
	 * @throws Throwable
	 */
	protected void removeElements (Executor executor, XmlDoc.Element metaType, String id, String objectType, XmlDoc.Element currentMeta) throws Throwable {
		XmlDoc.Element dicom = metaType.element("dicom");
		if (dicom!=null) {
			removeElementsDicom (executor, id, objectType, currentMeta);
		} 

		XmlDoc.Element bruker = metaType.element("bruker");
		if (bruker!=null) {
			removeElementsBruker (executor, id, objectType, currentMeta);	
		}
	}




	/**
	 * Update the meta-data on the  project object.  This function must
	 * do the actual update with the appropriate service (e.g. om.pssd.project.update).
	 * This function over-rides the default implementation.
	 * 
	 * @param id The citeable ID of the object to update
	 * @param meta The DICOM Study Metadata or Bruker identifier metadata. This class must understand the structure of this object
	 *      it's up to you what you put in it.  This class is invoked by the servuce nig.pssd.subject.meta.set and so its interface 
	 *      determines the structure
	 * @param privacyType The element to find the meta-data in the object description
	 * 					   For SUbjects and RSubjects should be one of "public", "private", "identity" 
	 *                     For other object types, should be "meta"
	 * @param docType the document type to write meta-data for.  The values must be mapped from the Study MetaData
	 * @param currentMeta  The meta-data that are attached to the asset (:foredit false)
	 * @throws Throwable
	 */
	protected void addTranslatedProjectDocument (Executor executor, String id, XmlDoc.Element meta, 
			String privacyType, String docType, XmlDoc.Element currentMeta) throws Throwable {
		if (meta==null) return;

		XmlDocMaker dm = null;

		// This doc type now retired in favour of generic daris:pssd-project-governance (which
		// the DICOM server could write to now if it wanted)
		// Leave as an example; it will never be called
		if (docType.equals("nig-daris:pssd-project")) {
			if (checkDocTypeExists(executor, "nig-daris:pssd-project")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType, new String[]{"action","merge"});
				boolean doIt = addProjectFacilityIDOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}
		} 

		// Update the Project
		if (dm!=null) {
			updateProject(executor, dm);
		}
	}

	/**
	 * Update the meta-data on the  subject object. This function must
	 * do the actual update with the appropriate service (e.g. om.pssd.subject.update).
	 * This function over-rides the default implementation.
	 * 
	 * @param id The citeable ID of the object to update
	 * @param meta The DICOM Study Metadata or Bruker identifier metadata 
	 * @param privacyType The element to find the meta-data in the object description
	 * 					   For SUbjects and RSubjects should be one of "public", "private", "identity" (RSubjects)
	 *                     For other object types, should be "meta"
	 * @param docType the document type to write meta-data for.  The values must be mapped from the Study MetaData
	 * @param currentMeta  The meta-data that are attached to the asset (:foredit false)
	 * @throws Throwable
	 */
	protected void addTranslatedSubjectDocument (Executor executor, String id, XmlDoc.Element meta, String privacyType, 
			String docType, XmlDoc.Element currentMeta) throws Throwable {
		if (meta==null) return;

		XmlDocMaker dm = null;
		if (docType.equals("nig-daris:pssd-identity")) {
			if (checkDocTypeExists(executor, "nig-daris:pssd-identity")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType);
				boolean doIt = addIdentityOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}
		} else if (docType.equals("nig-daris:pssd-human-identity")) {
			if (checkDocTypeExists(executor, "nig-daris:pssd-human-identity")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType);
				boolean doIt = addHumanIdentityOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}
		} else if (docType.equals("nig-daris:pssd-animal-subject")) {
			if (checkDocTypeExists(executor, "nig-daris:pssd-animal-subject")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType);
				boolean doIt = addAnimalSubjectOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}
		} else if (docType.equals("nig-daris:pssd-amrif-subject")) {
			if (checkDocTypeExists(executor, "nig-daris:pssd-amrif-subject")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType);
				boolean doIt = addaMRIFSubjectOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}
		} else if (docType.equals("nig-daris:pssd-artificial-subject")) {
			if (checkDocTypeExists(executor, "nig-daris:pssd-artificial-subject")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType);
				boolean doIt = addArtificialSubjectOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}

		}

		
		// Update the SUbject
		if (dm!=null) {
			dm.add("allow-incomplete-meta", true);
			dm.add("allow-invalid-meta", true);
			updateSubject(executor, dm);
		}

	}


	/**
	 * Update the meta-data on the study object.  This function must
	 * do the actual update with the appropriate service (e.g. om.pssd.project.update).
	 * This function over-rides the default implementation.
	 * 
	 * @param id The citeable ID of the object to update
	 * @param meta The DICOM Study Metadata or Bruker identifier metadata 
	 * @param privacyType The element to find the meta-data in the object description
	 * 					   For SUbjects and RSubjects should be one of "public", "private", "identity" 
	 *                     For other object types, should be "meta"
	 * @param docType the document type to write meta-data for.  The values must be mapped from the Study MetaData
	 * @param ns An addition namespace to be set on the meta-data being updated.  Its purpose is for
	 *       Method namespaces like cid_step that must be set on the Method specified Study meta-data
	 * @param currentMeta  The meta-data that are attached to the asset (:foredit false)
	 * @throws Throwable
	 */
	protected void addTranslatedStudyDocument (Executor executor, String id, XmlDoc.Element meta, 
			String privacyType, String docType, String ns, XmlDoc.Element currentMeta) throws Throwable {
		if (meta==null) return;

		// No DICOM mapping at this time

		// Bruker.  
		// This does not need to use the Method namespace because it uses its own specialised 'bruker' namespace
		XmlDocMaker dm = null;
		if (docType.equals("daris:bruker-study ")) {
			if (checkDocTypeExists(executor, "daris:bruker-study ")) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push(privacyType, new String[]{"action","merge"});
				boolean doIt = addStudyOuter (meta, currentMeta, dm);
				if (!doIt) dm = null;
			}
		} 

		// Update the Study
		if (dm!=null) {
			updateStudy(executor, dm);
		}
	}




	private boolean addProjectFacilityIDOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {

		return addProjectFacilityIDDICOM (meta.element("dicom"), currentMeta, dm) ||
				addProjectFacilityIDBruker (meta.element("bruker"), currentMeta, dm);
	}


	private boolean addIdentityOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		return addIdentityDICOM (meta.element("dicom"), currentMeta, dm) ||
				addIdentityBruker (meta.element("bruker"), currentMeta, dm);
	}

	private boolean addHumanIdentityOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		// DICOM only
		return addHumanIdentityDICOM (meta.element("dicom"), currentMeta, dm);
	}

	private boolean addAnimalSubjectOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {

		XmlDoc.Element dicom = meta.element("dicom");
		Boolean set = false;
		if (dicom!=null) {
			XmlDoc.Element subject = dicom.element("subject");
			if (subject!=null) {

				// Extract values from container
				Date dob = subject.element("dob")!=null ? subject.dateValue("dob") : null;
				String dobString = null;
				if (dob != null) {
					SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);
					// Drop time part of DOB
					dobString = DateUtil.formatDate(dob, false, null);
				}
				String gender = subject.value("sex");
				//
				set = addAnimalSubject (dobString, gender, currentMeta, dm);
			}
		}
		//
		XmlDoc.Element bruker = meta.element("bruker");
		if (bruker!=null) {
			String dob =null;      //  Not available
			String gender = bruker.value("gender");
			if (gender!=null) {
				if (gender.equalsIgnoreCase("M")) {
					gender = "male";
				} else if (gender.equalsIgnoreCase("F")) {
					gender = "female";
				} else {
					gender = "unknown";
				}
			}
			Boolean set2 = addAnimalSubject (dob, gender, currentMeta, dm);
			if (set2) set = true;
		}
		return set;
	}



	private boolean addArtificialSubjectOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {

		XmlDoc.Element dicom = meta.element("dicom");
		Boolean set = false;
		if (dicom!=null) {
			XmlDoc.Element subject = dicom.element("subject");
			if (subject!=null) {
				String name = subject.value("name").toUpperCase();
				if (name!=null) {
					String[] names = name.split(" ");
					Boolean isPhantom = false;

					// This document type is only used on non-humans so
					// no risk a Human will become a phantom !
					for (int i=0; i<names.length; i++) {
						if (names[i].contains("PHANTOM")) {
							isPhantom = true;
						}
					}
					if (isPhantom) {
						dm.push("nig-daris:pssd-artificial-subject");
						dm.add("type", "phantom");
						dm.pop();
						dm.pop();      // "public" or "private" pop
						dm.add("action", "merge");   // DOn't set if already set
						set = true;
					}
				}
			}
		}
		//
		XmlDoc.Element bruker = meta.element("bruker");
		if (bruker!=null) {
		}
		return set;
	}


	private boolean addaMRIFSubjectOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {

		// No mapping for DICOM data at this time.
		return addaMRIFSubjectBruker (meta.element("bruker"), currentMeta, dm);
	}

	private boolean addStudyOuter (XmlDoc.Element meta,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {

		// No mapping for DICOM data at this time.
		return addStudyBruker (meta.element("bruker"), currentMeta, dm);
	}



	/**
	 * Function to add the Project Facility ID  to the Project meta-data if it does not already exist
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addProjectFacilityIDDICOM (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// There is no really good candidate for the project ID in the
		// Study meta-data.  Perhaps the element
		// STUDY_DESCRIPTION = new DataElementTag(0x0008,0x1030);
		// would be ok.  

		// Extract DICOM meta data
		String projectDescription = sm.value("description");

		// We really can't know who generated this description.
		// We can't assume it's anything to do with the facility that
		// actually provides the data.
		String facilityType = "Other";   

		// Add the facility ID
		return addFacilityID (currentMeta, projectDescription, facilityType, dm);

	}

	/**
	 * Function to add the Subject ID (DICOM element (0010,0020)) to
	 * the SUbject meta-data if it does not already exist
	 * not already exist
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addIdentityDICOM (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// Extract DICOM meta data 
		String patientID = sm.value("subject/id");
		if (patientID == null) return false;

		// Set type of identity; i.e. who supplied this identity
		// These must match the values found in doc type nig-daris:pssd-identity
		String typeID = "Other";
		String scanFac = scannerFacility(sm);
		if (scanFac!=null) {			
			if (scanFac.equals(AMRIF_FACILITY)) {
				typeID = "Forey Small Animal MR Facility";
			} else if (scanFac.equals(RCH_FACILITY)) {
				typeID = "Royal Children's Hospital";
			} else if (scanFac.equals(MBCIU_FACILITY_PETCT)) {
				// This is the MBC ImagingUnit PET/CT
				// One day we will merge the 7T and PET/CT Authority
				typeID = "Melbourne Brain Centre Imaging Unit"; 
			} else if (scanFac.equals(MBCIU_FACILITY_7T)) {
				// This is the MBC ImagingUnit 7T
				typeID = "Melbourne Brain Centre Imaging Unit 7T";
			} else if (scanFac.equals(RHH_FACILITY)) {
				typeID = "Royal Hobart Hospital";
			} else if (scanFac.equals(LGH_FACILITY)) {
				typeID = "Launceston General Hospital";
			} else {
				typeID = "Other";
			}
		}

		// Add/merge the identity if needed.
		return addIdentity (currentMeta, patientID, typeID, dm);
	}


	/**
	 * Function to add the Subject ID (DICOM element (0010,0020)) to
	 * the SUbject meta-data if it does not already exist
	 * not already exist
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addHumanIdentityDICOM (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// Extract DICOM meta data 
		String name = sm.value("subject/name");
		if (name == null) return false;

		// After parsing into StudyMetadata the name is held as a DicomPersonName
		// It's then converted to a string for XML and any carets (^) are lost
		// We end up with <First> <Middle> <Last>
		String[] names = name.split(" ");
		String firstName = null;
		String lastName = null;
		int n = names.length;
		if (n<2) return false;

		firstName = names[0];
		lastName = names[n-1];

		// If the name already exists, we don't overwrite.
		if (currentMeta!=null) {
			Collection<XmlDoc.Element> identities = currentMeta.elements("nig-daris:pssd-human-identity");
			if (identities!=null) {
				for (XmlDoc.Element identity : identities) {
					String f = identity.value("first");
					String l = identity.value("last");
					if (f.equalsIgnoreCase(firstName) && l.equalsIgnoreCase(lastName)) {
						return false;
					}
				}
			}
		}

		// So we did not find this identity and need to add it. We don't
		// want to merge as that may overwrite legitimate and correct
		// identity information (maybe their name changed)
		dm.push("nig-daris:pssd-human-identity");
		dm.add("first", firstName);
		dm.add("last", lastName);
		dm.pop();
		dm.pop();      // "public" or "private" pop
		dm.add("action", "add");

		return true;
	}



	/**
	 * Function to add the Project Facility ID  to the Project meta-data if it does not already exist
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addProjectFacilityIDBruker (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// Extract Bruker meta data. Now the "project description", as used at aMRIF, is really just
		// a String (one word) describing the Project Name.  So it's legitimate to treat it
		// as a Facility ID
		String projectDescription = sm.value("project_descriptor");
		String facilityType = "aMRIF";                           // Because this is a NIG class, we are allowed to 'know' this

		// Add/merge the facility ID
		return addFacilityID (currentMeta, projectDescription, facilityType, dm);

	}

	/**
	 * Function to add the Subject ID (DICOM element (0010,0020)) to
	 * the SUbject meta-data if it does not already exist
	 * not already exist
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addIdentityBruker (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// Extract Bruker meta data 
		String animalID = sm.value("animal_id");

		// Set type of identity; i.e. who supplied this identity
		// OK because this is a NIG class and should only be utilised at NIG (for now)
		// TODO: find some way of getting the actual station into here.
		String typeID = "Florey Small Animal MR Facility";    


		// Add the identity if needed.
		return addIdentity (currentMeta, animalID, typeID, dm);

	}

	/**
	 * Function to add the meta-data parsed from the FNI Small ANimal Facility subject ID coded strings
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addaMRIFSubjectBruker (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// Add/merge the identity if needed.
		return addMergeaMRIFSubject (currentMeta, sm.value("animal_id"), sm.value("gender"), 
				sm.value("exp_group"), sm.value("vivo"), dm);
	}

	/**
	 * Function to add the meta-data parsed from the FNI Small ANimal Facility subject ID coded strings
	 * 
	 * @param executor
	 * @param currentMeta
	 * @param cid
	 * @throws Throwable
	 */	
	private boolean addStudyBruker (XmlDoc.Element sm,  XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {
		if (sm==null) return false;

		// Add/merge the identity if needed.
		Date date = sm.element("date").hasValue() ? sm.dateValue("date") : null;
		String dateStr = null;
		if (date!=null) {
			dateStr = DateUtil.formatDate(date, "dd-MMM-yyyy");
		}

		return addMergeaMRIFStudy (currentMeta, sm.value("coil"), dateStr, dm);
	}


	private boolean addMergeaMRIFStudy (XmlDoc.Element currentMeta, String coil, String date,
			XmlDocMaker dm) throws Throwable {

		// Set updated meta-data
		// We add a new document with the details.  SHould never be more than one...
		Boolean someMeta = false;

		// Should get this namespace from a central class...
		dm.push("daris:bruker-study ", new String[] { "ns", "bruker"}); 
		if (coil != null) {
			dm.add("coil", coil);
			someMeta = true;
		}
		if (date!=null) {
			dm.add("date", date);
			someMeta = true;
		}
		dm.pop();
		dm.pop();      // "meta" pop
		return someMeta;
	}


	private boolean addFacilityID (XmlDoc.Element currentMeta,  String projectID, String idType, XmlDocMaker dm) throws Throwable {


		// See if this specific identity already exists on the object
		Collection<XmlDoc.Element> identities = null;
		if (currentMeta!=null) {
			identities = currentMeta.elements("nig-daris:pssd-project");
			if (identities != null) {
				for (XmlDoc.Element el : identities) {
					String id = el.value("facility-id");
					String type = el.value("facility-id/@type");

					// If we have this specific identity already, return
					if(id!=null&&type!=null) {
						if (id.equals(projectID) && type.equals(idType)) return false;
					}
				}
			}
		}

		// So we did not find this identity and need to add it.
		dm.push("nig-daris:pssd-project");
		dm.add("facility-id", new String[] { "type", idType }, projectID);		
		dm.pop();

		// We want to merge  this identity with others on the same document
		dm.pop();      // "meta" pop
		return true;
	}

	private boolean addIdentity (XmlDoc.Element currentMeta,  String subjectID, String typeID, XmlDocMaker dm) throws Throwable {

		if (subjectID==null || typeID==null) return false;

		// See if this specific identity already exists on the object
		Collection<XmlDoc.Element> identities = null;
		if (currentMeta!=null) {
			identities = currentMeta.elements("nig-daris:pssd-identity");
			if (identities != null) {
				for (XmlDoc.Element identity : identities) {
					Collection<XmlDoc.Element> els = identity.elements("id");
					if (els!=null) {
						for (XmlDoc.Element el : els) {
							String id = el.value();
							String type = el.value("@type");

							// If we have this specific identity already, return
							if(id!=null&&type!=null) {
								if (id.equals(subjectID) && type.equals(typeID)) return false;
							}
						}
					}
				}
			}
		}

		// So we did not find this identity and need to add it.
		dm.push("nig-daris:pssd-identity");
		dm.add("id", new String[] { "type", typeID }, subjectID);		
		dm.pop();

		// We want to merge  this identity with others on the same document
		dm.pop();      // "public" or "private" pop
		dm.add("action", "merge");
		return true;
	}


	private boolean addMergeaMRIFSubject (XmlDoc.Element currentMeta, String animalID, String gender, 
			String experimentalGroup, String vivo, XmlDocMaker dm) throws Throwable {

		// Set updated meta-data
		// We add a new document with the details.  SHould never be more than one so a merge is ok
		Boolean someMeta = false;
		dm.push("nig-daris:pssd-amrif-subject");
		if (animalID!=null) {
			dm.add("id", animalID);
			someMeta = true;
		}
		if (gender!=null) {
			// Check values are legal
			dm.add("gender", gender);
			someMeta = true;
		}
		if (experimentalGroup!=null) {
			dm.add("group", experimentalGroup);
			someMeta = true;
		}
		if (vivo!=null) {
			// Check values are legal
			dm.add("vivo", vivo);
			someMeta = true;
		}
		dm.pop();

		// Merge these details with extant
		dm.pop();      // "public" or "private" pop
		dm.add("action", "merge");	
		return someMeta;
	}



	private boolean addAnimalSubject (String dob, String gender, XmlDoc.Element currentMeta,  XmlDocMaker dm) throws Throwable {

		// Get current meta-data set on the object for appropriate DocType
		if (currentMeta!=null) {
			XmlDoc.Element subjectMeta = currentMeta.element("nig-daris:pssd-animal-subject");

			// We assume that if the element is already set on the object that it is correct
			if (subjectMeta!=null) {
				String currGender = subjectMeta.value("gender");
				if (currGender!=null) gender = currGender;
				//
				String currDate = subjectMeta.value("birthDate");
				if (currDate != null) {
					// Make sure no vestigial Dates with Times are hanging about
					int l = currDate.length();
					if (l>11) {
						String t = DateUtil.convertDateString(currDate, "dd-MMM-yyyy HH:mm:ss", "dd-MMM-yyyy");
						dob = t;
					}
					dob = currDate;
				}
			}
		}

		// Set updated meta-data
		if (gender!=null || dob!=null) {
			dm.push("nig-daris:pssd-animal-subject");
			if (gender!=null) dm.add("gender", gender);
			if (dob != null) dm.add("birthDate", dob);
			dm.pop();
		} else {
			return false;
		}

		// Merge these details
		dm.pop();      // "public" or "private" pop
		dm.add("action", "merge");	
		return true;
	}



	/**
	 * What Facility are these data from?
	 * @param sm
	 * @return
	 */
	public String scannerFacility (XmlDoc.Element sm) throws Throwable {
		String institution = sm.value("institution");
		String station = sm.value("station");
		//
		if (institution!=null && station!=null) {
			String i2 = institution.toUpperCase();
			String s2 = station.toUpperCase();
			if (i2.contains("FLOREY") && s2.equals("EPT")) {
				return AMRIF_FACILITY;
			} 
		}
		if (station!=null) {
			if (station.equalsIgnoreCase("MRC35113")) {
				return RCH_FACILITY;
			} else if (station.equalsIgnoreCase("RHH-OC0") || station.equalsIgnoreCase("RHHOC0") || station.equalsIgnoreCase("af219_ws")) {
				return RHH_FACILITY;
			} else if (station.equalsIgnoreCase("CTAWP71222")) {
				return MBCIU_FACILITY_PETCT;
			} else if (station.equalsIgnoreCase("MRC18978")) {
				return MBCIU_FACILITY_7T;
			} else if (station.equalsIgnoreCase("EPT")) {
				return AMRIF_FACILITY;
			} else if (station.equalsIgnoreCase("MRC26798") || station.equalsIgnoreCase("MEDPC")) {
				return LGH_FACILITY;
			}
		}
		//
		return institution;
	}


	/**
	 * Remove the mapped elements for when we are considering DICOM meta-data.  
	 * 
	 * @param executor
	 * @param id
	 * @param objectType
	 * @param currentMeta
	 * @throws Throwable
	 */
	private void removeElementsDicom (Executor executor, String id, String objectType, XmlDoc.Element currentMeta) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", id);
		//
		boolean some = false;
		if (objectType.equals(DomainMetaData.PROJECT_TYPE)) {
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-project", new String[]{"facility-id"})) some = true;
		} else if (objectType.equals(DomainMetaData.SUBJECT_TYPE)) {
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-identity", new String[]{"id"})) some = true;
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-animal-subject", new String[]{"gender", "birthDate"})) some = true;
		} else if (objectType.equals(DomainMetaData.STUDY_TYPE)) {
			// No DICOM mappings for now
		}
		//
		if (some) {
			executor.execute("asset.set", dm);
		}
	}


	/**
	 * Remove the mapped elements for when we are considering Bruker meta-data.  
	 * 
	 * @param executor
	 * @param id
	 * @param objectType
	 * @param currentMeta
	 * @throws Throwable
	 */
	private void removeElementsBruker (Executor executor, String id, String objectType, XmlDoc.Element currentMeta) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", id);
		//
		boolean some = false;
		if (objectType.equals(DomainMetaData.PROJECT_TYPE)) {
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-project", new String[]{"facility-id"})) some = true;
		} else if (objectType.equals(DomainMetaData.SUBJECT_TYPE)) {
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-identity", new String[]{"id"})) some = true;
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-animal-subject", 
					new String[]{"gender", "birthDate"})) some = true;
			if (prepareRemovedMetaData (dm, currentMeta, "nig-daris:pssd-amrif-subject", 
					new String[]{"id", "gender", "group", "vivo"})) some = true;
		} else if (objectType.equals(DomainMetaData.STUDY_TYPE)) {
			if (prepareRemovedMetaData (dm, currentMeta, "daris:bruker-study ", 
					new String[]{"coil", "date"})) some = true;
		}
		//
		if (some) {
			executor.execute("asset.set", dm);
		}
	}
}
