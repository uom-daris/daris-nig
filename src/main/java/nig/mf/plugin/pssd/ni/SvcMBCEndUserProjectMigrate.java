package nig.mf.plugin.pssd.ni;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginTask;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCEndUserProjectMigrate extends PluginService {

	private class ProjectPair {
		private String subjectCID_ = null;
		private String patientID_ = null;
		private String archiveSubjectCID_ = null;
		private String archivePatientID_ = null;
		private ProjectPair (String subjectCID, String patientID, String archiveSubjectCID, String archivePatientID) {
			subjectCID_ = subjectCID;
			patientID_ = patientID;
			archiveSubjectCID_ = archiveSubjectCID;
			archivePatientID_ = archivePatientID;
		}
		private String getSubjectCID () {return subjectCID_;};
		private String getPatientID () {return patientID_;};
		private String getArchiveSubjectCID () {return archiveSubjectCID_;};
		private String getArchivePatientID () {return archivePatientID_;};
	}

	private Interface _defn;
	private static String ARCHIVE_CID = "1.10.3";
	private static final String OTHER_ID_TYPE = "Melbourne Brain Centre Imaging Unit";
	private static final String OTHER_ID_TYPE_LEGACY = "Melbourne Brain Centre Imaging Unit 7T Legacy";


	public SvcMBCEndUserProjectMigrate() {
		_defn = new Interface();
		_defn.add(new Element("pid", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate from.", 1, 1));
		_defn.add(new Element("list-only", BooleanType.DEFAULT, "Just list, don't migrate any data (defaults to true).", 0, 1));
		_defn.add(new Element("clean-up", BooleanType.DEFAULT, "Clean up (destroy) Subjects that are merged into one new one (default false).", 0, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.enduser.project.migrate";
	}

	public String description() {

		return "Migrates an MBC DaRIS end-user project from visit-based Subject IDs to subject-based subject IDs.";
	}

	public Interface definition() {

		return _defn;
	}

	public Access access() {

		return ACCESS_MODIFY;
	}


	@Override
	public int minNumberOfOutputs() {
		return 0;
	}

	@Override
	public int maxNumberOfOutputs() {
		return 0;
	}

	public boolean canBeAborted() {

		return true;
	}

	public void execute(XmlDoc.Element args, Inputs inputs, Outputs outputs, XmlWriter w)
			throws Throwable {

		// Parse arguments
		String projectCID = args.stringValue("pid");
		Boolean listOnly = args.booleanValue("list-only", true);
		Boolean cleanUp = args.booleanValue("clean-up", false);
		//
		XmlDoc.Element projectMeta = AssetUtil.getAsset(executor(), projectCID, null);
		String methodCID = projectMeta.value("asset/meta/daris:pssd-project/method/id");

		// This map tells us which origin Subjects have already been handled.
		// For example, SUbject 2 is found to have duplicates. At that
		// point all of the duplicates are combined into one
		// and the map will be set to TRUE for those subjects
		HashMap<String,Boolean> handled  = new HashMap<String,Boolean>();

		// Keep a list of SUbjects that we create when merging duplicates
		ArrayList<String> newSubjects = new ArrayList<String>();

		// Make sure there is no raw data
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("where", "cid starts with '" + projectCID + "' and daris:siemens-raw-mr-study has value");
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		String id = r.value("id");
		if (id!=null) {
			throw new Exception ("This project has raw data");
		}

		// Fetch the Subjects in the project (visit based)
		Collection<String> subjectIDs = findSubjects (executor(), projectCID);

		// Iterate through SUbjects and initialise map
		for (String subjectID : subjectIDs) {
			handled.put(subjectID, false);
		}

		// Now iterate and handle subjects
		for (String subjectID : subjectIDs) {
			PluginTask.checkIfThreadTaskAborted();
			w.push("subject");
			w.add("id", subjectID);
			if (!handled.get(subjectID)) {
				// OK This one is still in our list.  We didn't destroy it in a duplicate merge process
				migrateSubject (executor(), cleanUp, listOnly, projectCID, methodCID, subjectID, handled, newSubjects, w);
			} else {
				w.add("handled", "true");
			}
			w.pop();
		}

	}


	private Collection<String> findSubjects (ServiceExecutor executor, String projectID) throws Throwable {

		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", projectID);
		dm.add("sort", "true");
		XmlDoc.Element r = executor().execute("om.pssd.collection.member.list", dm.root());
		return r.values("object/id");

	}


	private void  migrateSubject (ServiceExecutor executor,  Boolean cleanUp, Boolean listOnly, String projectCID, String methodCID, String subjectCID, 
			HashMap<String,Boolean> handled, Collection<String> newSubjects, XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// Fetch DICOM meta-data
		XmlDoc.Element subjectMeta = AssetUtil.getAsset(executor(), subjectCID, null);
		XmlDoc.Element dicomMeta =  subjectMeta.element("asset/meta/mf-dicom-patient");
		if (dicomMeta==null) {
			throw new Exception ("THe DICOM meta-data on subject " + subjectCID + " is missing");
		}
		w.add(dicomMeta);
		DICOMPatient dp = new DICOMPatient(dicomMeta);



		// Determine if this SUbject has any duplicates in this project
		// For 7T Humans, we use the H number (study based). We look it up in the Human archive
		// project and find its unique Patient ID.
		// For other Subjects, we just use the Patient ID and look for it elsewhere in the projects
		Collection<ProjectPair> duplicateSubjects = findDuplicates (executor, projectCID, subjectCID, handled, newSubjects, dp);
		Boolean hasDuplicates = false;
		if (duplicateSubjects==null) {
			// We could not find the DICOM patient ID on a Study (the H number) in the archive
			w.push("duplicates", new String[]{"status", "failed"});
			w.add("patient-id", new String[]{"status", "not-found-in-archive"}, dp.getID());
			w.pop();
		} else {
			hasDuplicates = !(duplicateSubjects.isEmpty());
			if (hasDuplicates) {
				w.push("duplicates", new String[]{"status", "some"});
				for (ProjectPair duplicateSubject : duplicateSubjects) {
					w.add("duplicate", new String[]{"archive-subject", duplicateSubject.getArchiveSubjectCID(), "archive-patient-id", duplicateSubject.getArchivePatientID(), "patient-id", duplicateSubject.getPatientID()}, duplicateSubject.getSubjectCID());
				}
				w.pop();
			} else {
				w.push("duplicates", new String[]{"status", "none"});
				w.pop();
			}
		}

		// If it does have duplicates, we handle them all now by making a new Subject and migrating all data to it
		if (!listOnly) {
			if (hasDuplicates) {
				mergeDuplicates(executor, projectCID, methodCID,  subjectCID, duplicateSubjects, handled, cleanUp, w);
			} else {
				PluginTask.checkIfThreadTaskAborted();

				// Work out if this is a 7T Human 
				String originalPatientID = dp.getID();    // H number
				if (originalPatientID.length()>=3 && originalPatientID.substring(0, 3).equals("H00")) {
					// We have a 7T Human

					// Look up the FMP-based patient ID from the archive by finding the H Number in a Study and then finding its parent Subject
					String archiveSubjectCID = findSubjectInArchive (executor, originalPatientID);
					XmlDoc.Element archiveSubjectAsset = AssetUtil.getAsset(executor, archiveSubjectCID, null);
					String archivePatientID = archiveSubjectAsset.value("asset/meta/mf-dicom-patient/id");
					w.add("archive-patient-id", archivePatientID);

					// Update Subject meta-data
					updateSubjectMetaData (executor, subjectCID, archivePatientID, originalPatientID);
					w.add("subject-meta-updated", true);

					// Find Studies
					Collection<String> studyCIDs = findStudies (executor, subjectCID);

					// Update Study Meta-data
					if (studyCIDs!=null) {
						for (String studyCID : studyCIDs) {
							w.push("study");
							w.add("id", studyCID);
							PluginTask.checkIfThreadTaskAborted();

							// FInd the FMP generated visit ID. It's possible we can't find it.
							String archiveVisitID = findVisitIDFromArchive (executor, studyCID);
							if (archiveVisitID!=null) {
								w.add("archive-visit-id", archiveVisitID);

								// Update the Study meta-data with the old H number and the new visit ID
								updateStudyMetaData (executor, studyCID, originalPatientID, archiveVisitID);
								w.add("study-meta-data-updated", studyCID);

								// Update the DAtaSets for this Study so that the AccessionNumber is set to the FMP visit ID
								String t = updateDataSetMetaData (executor, studyCID, archivePatientID, archiveVisitID);			
								w.add("datasets-meta-data-updated",  t);
							} else {
								w.add("archive-visit-id", "not-found");
							}
							w.pop();
						}
					}
				}
			}
		}
	}



	private void mergeDuplicates (ServiceExecutor executor, String projectCID, String methodCID, String subjectCID, 
			Collection<ProjectPair> duplicateSubjects,  HashMap<String,Boolean> handled, Boolean cleanUp, XmlWriter w) throws Throwable {

		// Fetch SUbject asset
		XmlDoc.Element r = AssetUtil.getAsset(executor, subjectCID, null);
		XmlDoc.Element dicomMeta = r.element("asset/meta/mf-dicom-patient");
		DICOMPatient dp = new DICOMPatient(dicomMeta);
		String patientID = dp.getID();         // The H Number

		// We need to know the new patient ID. For 7T Humans, it's contained in the ProjectPair Objects.
		// For other Subject types, those are null, and we just need the patient ID as it was on the original;
		// We can use the first duplicate subject container objects to work this out, as the information
		// is duplicated
		Iterator<ProjectPair> it = duplicateSubjects.iterator();	
		ProjectPair pp = it.next();

		// The following is null for non 7T Human subjects
		// It is the same for all members of the collection
		String archivePatientID = pp.getArchivePatientID();

		// Create a new Subject and update the patient ID if needed for 7T HUmans
		String newSubjectCID = createSubject (executor, projectCID, methodCID, subjectCID, patientID, archivePatientID, w);
		w.add("new-id", new String[]{"status", "created"}, newSubjectCID);

		// Migrate the Studies over and update the meta-data also (indexed and DICOM).
		//
		// First the primary Subject Studies
		Collection<String> studyCIDs = findStudies(executor, subjectCID);
		w.push("original");
		w.add("subject-id", subjectCID);
		w.add("patient-id", patientID);
		w.add("archive-patient-id", archivePatientID);
		migrateStudies (executor, newSubjectCID, studyCIDs, patientID, archivePatientID, w);
		w.pop();
		handled.put(subjectCID, true);

		// Now the duplicate Subject Studies
		for (ProjectPair duplicateSubject : duplicateSubjects) {
			w.push("duplicate");
			w.add("subject-id", duplicateSubject.getSubjectCID());
			w.add("patient-id", duplicateSubject.getPatientID());
			w.add("archive-subject-id", duplicateSubject.getArchiveSubjectCID());
			w.add("archive-patient-id", duplicateSubject.getArchivePatientID());
			studyCIDs = findStudies (executor, duplicateSubject.getSubjectCID());
			migrateStudies (executor, newSubjectCID, studyCIDs, duplicateSubject.getPatientID(), archivePatientID, w);
			handled.put(duplicateSubject.getSubjectCID(), true);
			w.pop();
		}

		// Clean up the original merged Subjects
		if (cleanUp) {
			w.push("destroyed");
			destroyObject (executor, subjectCID);
			w.add("subject", subjectCID);
			for (ProjectPair duplicateSubject : duplicateSubjects) {
				destroyObject (executor, duplicateSubject.getSubjectCID());
				w.add("subject", duplicateSubject.getSubjectCID());
			}
			w.pop();
		}
	}

	private void destroyObject (ServiceExecutor executor, String cid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", cid);
		executor.execute("om.pssd.object.destroy", dm.root());
	}

	private void migrateStudies (ServiceExecutor executor, String subjectCID, Collection<String> studyCIDs,
			String originalPatientID, String archivePatientID, XmlWriter w) throws Throwable {
		if (studyCIDs==null) return;
		w.push("study");

		// The Studies passed in all come from one Subject. So they all pertain to the same
		// original H Number (originalPatientID);
		for (String studyCID : studyCIDs) {
			w.add("original-patient-id", originalPatientID);

			// Migrate to new parent
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("cid", studyCID);
			dm.add("to", subjectCID);
			String newStudyCID = null;
			XmlDoc.Element r = executor.execute("daris.study.copy", dm.root());
			newStudyCID = r.value("study/@cid");
			w.add("migrated", new String[]{"old-study-id", studyCID}, newStudyCID);

			if (archivePatientID!=null) {
				// We have a 7T Human so we can set the meta-data more precisely
				// For non 7T Humans, there are no meta-data updates to make
				// We might not find it in the archive if an error was made (not sent)
				String archiveVisitID = findVisitIDFromArchive (executor, studyCID);
				if (archiveVisitID!=null) {
					w.add("archive-visit-id", archiveVisitID);
					updateStudyMetaData (executor, newStudyCID, originalPatientID, archiveVisitID);
					w.add("study-meta-data-updated", newStudyCID);

					// Find the DAtaSets for this Study
					String t = updateDataSetMetaData (executor, newStudyCID, archivePatientID, archiveVisitID);			
					w.add("datasets-meta-data-updated",  t);
				}
			}
		}
		w.pop();
	}

	private void updateStudyMetaData (ServiceExecutor executor, String studyCID, String patientID, String visitID) throws Throwable {

		XmlDocMaker dm = new XmlDocMaker ("args");
		dm.add("cid", studyCID);
		dm.push("meta");
		dm.push("daris:pssd-study");   // No namespace

		// Legacy H Number (the original Patient ID). It's constant for all of
		// the Studies that were attached to that Subject
		dm.add("other-id", new String[]{"type", OTHER_ID_TYPE_LEGACY}, patientID); 

		// We can fetch the new, FMP allocated Visit ID from the Archive Project
		// We are only dealing with DICOM Data
		// So find the Study by UID
		// FInd the other-id set there
		if (visitID!=null) {
			dm.add("other-id", new String[]{"type", OTHER_ID_TYPE}, visitID); 
		}
		dm.pop();
		dm.pop();
		executor.execute("asset.set", dm.root());
	}


	private String updateDataSetMetaData (ServiceExecutor executor, String studyCID, String patientID, String visitID) throws Throwable {
		Collection<String> dataSetCIDs = findDataSets (executor, studyCID);
		String t = "";
		for (String dataSetCID : dataSetCIDs) {
			XmlDoc.Element r = AssetUtil.getAsset(executor, dataSetCID, null);
			XmlDoc.Element dicomMeta = r.element("asset/meta/mf-dicom-series");
			PluginTask.checkIfThreadTaskAborted();

			// Modify the DICOM meta-data 
			if (dicomMeta!=null) {
				XmlDocMaker dm = new XmlDocMaker("args");
				dm.add("cid", dataSetCID);
				// Write the FMP subject ID into Patient ID
				if (patientID!=null) {
					dm.push("element", new String[]{"action", "merge", "tag", "00100020"});
					dm.add("value", patientID);
					dm.pop();
				}
				// Write the FMP visit ID into the Accession Number
				if (visitID!=null)  {
					dm.push("element", new String[]{"action", "merge", "tag", "00080050"});
					dm.add("value", visitID);
					dm.pop();
				}
				executor.execute("daris.dicom.metadata.set", dm.root());
				t += dataSetCID + " ";

				// Prune DataSet
				dm = new XmlDocMaker("args");
				dm.add("id", CiteableIdUtil.cidToId(executor, dataSetCID));
				executor.execute("asset.prune", dm.root());
			}
		}
		return t;
	}


	private String findVisitIDFromArchive (ServiceExecutor executor, String studyCID) throws Throwable {

		// Get Study Asset
		XmlDoc.Element studyMeta = AssetUtil.getAsset(executor, studyCID, null);
		String uid = studyMeta.value("asset/meta/mf-dicom-study/uid");
		if (uid==null) return null;

		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("where", "cid starts with '" + ARCHIVE_CID + 
				"' and xpath(mf-dicom-study/uid)='" + uid + "'");
		dm.add("action", "get-cid");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		String archiveStudyCID = r.value("cid");
		if (archiveStudyCID==null) return null;   // We didn't find in archive

		// Get Archive Study asset
		XmlDoc.Element archiveStudyMeta = AssetUtil.getAsset(executor, archiveStudyCID, null);
		XmlDoc.Element t = archiveStudyMeta.element("asset/meta/daris:pssd-study");

		// Now fetch the FMP Visit ID
		return archiveStudyMeta.value("asset/meta/daris:pssd-study/other-id[@type='" + OTHER_ID_TYPE + "']");
	}

	private Collection<String> findDataSets (ServiceExecutor executor, String studyCID) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("size", "infinity");
		dm.add("where", "cid starts with '" + studyCID + "' and model='om.pssd.dataset'");
		dm.add("action", "get-cid");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		return r.values("cid");
	}

	private Collection<String> findStudies (ServiceExecutor executor, String subjectCID) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("size", "infinity");
		dm.add("where", "cid starts with '" + subjectCID + "' and model='om.pssd.study'");
		dm.add("action", "get-cid");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		return r.values("cid");
	}

	private String createSubject (ServiceExecutor executor, String projectCID,  String methodCID, String oldSubjectCID, 
			String patientID, String archivePatientID, XmlWriter w) throws Throwable {

		// TBD set subject name ??

		// Clone the subject and create ExMethod
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", oldSubjectCID);
		dm.add("fillin", false);
		XmlDoc.Element r = executor.execute("om.pssd.subject.clone", dm.root());
		String newSubjectCID = r.value("id");
		String exMethodCID = r.value("id/@mid");

		// Update the patient ID. We only do this for 7T Humans for which the new ID
		// is the one allocated by FMP
		if (archivePatientID!=null) {
			updateSubjectMetaData (executor, newSubjectCID, archivePatientID, patientID);
		}
		return newSubjectCID;

	}

	private void updateSubjectMetaData (ServiceExecutor executor, String subjectCID, String newPatientID, String oldPatientID) throws Throwable {

		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", subjectCID);
		dm.push("meta", new String[]{"action", "merge"});
		dm.push("mf-dicom-patient", new String[]{"ns", "pssd.private"});
		dm.add("id", newPatientID);
		dm.pop();
		// In the Archive the name is <FMP Patient ID>-<LastName>. Since, in general, the
		// Subjects are anonmymized  in end-user projects, we just set the name to the 
		// FMP Patient iD
		dm.push("daris:pssd-object");
		dm.add("name", newPatientID); 
		dm.pop();

		// We also transfer the FMP SUbject ID into the identity meta-data
		dm.push("nig-daris:pssd-identity", new String[]{"ns", "pssd.public"});
		dm.add("id", new String[]{"type", "Melbourne Brain Centre Imaging Unit"}, newPatientID);
		dm.pop();
		//
		dm.pop();
		executor.execute("asset.set", dm.root());

		// Remove legacy ID (if not existing it does not cause exception)
		if (oldPatientID!=null) {
			dm = new XmlDocMaker("args");
			dm.add("cid", subjectCID);
			dm.push("meta", new String[]{"action", "remove"});
			dm.push("nig-daris:pssd-identity", new String[]{"ns", "pssd.public"});
			dm.add("id", new String[]{"type", "Melbourne Brain Centre Imaging Unit 7T"}, oldPatientID);
			dm.pop();
			dm.push("nig-daris:pssd-identity", new String[]{"ns", "pssd.public"});
			dm.add("id", new String[]{"type", "Other"}, oldPatientID);
			dm.pop();
			dm.pop();
			executor.execute("asset.set", dm.root());
		}
	}


	/**
	 * FInd duplicates of this Subject in this project
	 * 
	 * @param executor
	 * @param handled
	 * @param newSubjects
	 * @param dicomMeta
	 * @return
	 * @throws Throwable
	 */
	private Collection<ProjectPair> findDuplicates (ServiceExecutor executor, String projectCID, String subjectCID, HashMap<String,Boolean> handled, Collection<String> newSubjects, DICOMPatient dp) throws Throwable {

		ArrayList<ProjectPair> duplicates = new ArrayList<ProjectPair>();

		String patientID = dp.getID();
		if (patientID.length()>=3 && patientID.substring(0, 3).equals("H00")) {

			// We have a 7T Human number
			// In the archive, find the Subject parent for a Study with this patient ID (an H Number) as a Study ID
			String archiveSubjectCID = findSubjectInArchive (executor, patientID);
			if (archiveSubjectCID==null) return null;
			//
			XmlDoc.Element archiveSubjectAsset = AssetUtil.getAsset(executor,  archiveSubjectCID,  null);
			XmlDoc.Element archiveDICOMMeta =  archiveSubjectAsset.element("asset/meta/mf-dicom-patient");
			if (archiveDICOMMeta==null) return null;
			String archivePatientID = archiveDICOMMeta.value("id");
			if (archivePatientID==null) return null;

			// Now, for each other Subject in this project, find it's equivalent Subject in the archive
			// If it matches the Subject ID for the Subject of interest, we have a duplicate.
			Collection<String> subjectCIDs = findSubjects (executor, projectCID);
			for (String subjectCID2 : subjectCIDs) {
				PluginTask.checkIfThreadTaskAborted();

				// Exclude this subject
				if (!subjectCID.equals(subjectCID2)) {

					// Exclude newly created Subjects
					if (!isNewSubject(subjectCID2, newSubjects)) {

						// Find its patient ID (H number)
						XmlDoc.Element asset2 = AssetUtil.getAsset(executor, subjectCID2, null);
						XmlDoc.Element dicomMeta2 =  asset2.element("asset/meta/mf-dicom-patient");
						if (dicomMeta2==null) {
							throw new Exception ("THe DICOM meta-data on subject " + subjectCID2 + " is missing");
						}
						DICOMPatient dp2 = new DICOMPatient(dicomMeta2);
						String patientID2 = dp2.getID();
						if (patientID2.length()>=3 && patientID2.substring(0, 3).equals("H00")) {
							// If 7T Human handle
							// Look up in Archive via H Number
							String archiveSubjectCID2 = findSubjectInArchive (executor, patientID2);
							if (archiveSubjectCID2==null) {
								// TBD What to do ?
							}

							// Add duplicate to list
							if (archiveSubjectCID.equals(archiveSubjectCID2)) {
								duplicates.add(new ProjectPair(subjectCID2, patientID2, archiveSubjectCID, archivePatientID));
							}
						} else {
							// Non-human Handled below
						}
					}
				}
			}
		} else {
			// We have a non 7T human
			// Just see if can find duplicates by patient ID

			XmlDocMaker dm = new XmlDocMaker("args");
			String where =  "xpath(mf-dicom-patient/id)='" + patientID + "' and (cid starts with '" + projectCID + 
					"' and not(cid='" + subjectCID + "')) and model='om.pssd.subject'";
			dm.add("where", where);
			dm.add("size", "infinity");
			dm.add("action", "get-cid");
			XmlDoc.Element r = executor.execute("asset.query", dm.root());
			Collection<String> subjectCIDs = r.values("cid");
			if (subjectCIDs!=null) {
				for (String subjectCID2 : subjectCIDs) {
					// Exclude newly created Subjects
					if (!isNewSubject(subjectCID2, newSubjects)) {
						// No archive information.
						duplicates.add(new ProjectPair(subjectCID2, patientID, null, null));
					}
				}
			}		
		}

		//
		return duplicates;
	}

	private Boolean isNewSubject (String subject, Collection<String> newSubjects) {
		for (String newSubject : newSubjects) {
			if (subject.equals(newSubject)) return true;
		}
		return false;

	}


	private String findSubjectInArchive (ServiceExecutor executor, String patientID) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("size", "infinity");
		String where = "cid starts with '" + ARCHIVE_CID + "' and  model='om.pssd.study' and xpath(daris:pssd-study/other-id)='"+patientID +"'";
		dm.add("where", where);
		dm.add("action", "get-cid");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		String archiveStudyCID = r.value("cid");
		if (archiveStudyCID==null) return null;

		// Now find the subject CID in the archive 
		return CiteableIdUtil.getSubjectId(archiveStudyCID);
	}

}
