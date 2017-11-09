package nig.mf.plugin.pssd.ni;

import java.io.ByteArrayInputStream;
import java.util.Collection;

import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginTask;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCNonHumanProjectMigrate extends PluginService {

	private Interface _defn;

	public SvcMBCNonHumanProjectMigrate() {
		_defn = new Interface();
		_defn.add(new Element("from", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate from.", 1, 1));
		_defn.add(new Element("to", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate to. Defaults to 'to'", 0, 1));
		_defn.add(new Element("idx", StringType.DEFAULT, "The start idx of the first subject to consider (defaults to 1).", 0, 1));
		_defn.add(new Element("size", StringType.DEFAULT, "The number of subjects to find (defaults to all).", 0, 1));
		_defn.add(new Element("list-only", BooleanType.DEFAULT, "Just list, don't migrate any data (defaults to true).", 0, 1));
		_defn.add(new Element("clone-content", BooleanType.DEFAULT, "Actually clone the content of the DataSets.  If false (the default), the DataSets are cloned but without content.", 0, 1));
		_defn.add(new Element("copy-raw", BooleanType.DEFAULT, "If true (default), when cloning the Siemens RAW (only) DataSet copy the content.  If false, the RAW content is moved.  DICOM content is always copied.", 0, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.nonhuman.project.migrate";
	}

	public String description() {

		return "Migrates an MBC DaRIS non-human project from visit-based Subject IDs to subject-based subject IDs.  If an output is supplied it generates a CSV file mapping DaRIS ID to FMP ID.";
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
		return 1;
	}

	public boolean canBeAborted() {

		return true;
	}

	public void execute(XmlDoc.Element args, Inputs inputs, Outputs outputs, XmlWriter w)
			throws Throwable {

		// Parse arguments
		String oldProjectID = args.stringValue("from");
		String newProjectID = args.stringValue("to", oldProjectID);
		String size = args.stringValue("size");
		String idx = args.stringValue("idx");;
		Boolean listOnly = args.booleanValue("list-only", true);
		Boolean cloneContent = args.booleanValue("clone-content", false);
		//
		Boolean copyRawContent = args.booleanValue("copy-raw",true);
		//
		XmlDoc.Element projectMeta = AssetUtil.getAsset(executor(), oldProjectID, null);
		String methodID = projectMeta.value("asset/meta/daris:pssd-project/method/id");

		// Optional output CSV file
		PluginService.Output output = null;
		String type = "text/csv";
		StringBuilder sb = new StringBuilder();

		// Fetch the Subjects in the old archive project (visit based)
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", oldProjectID);
		if (idx!=null) {
			dm.add("idx", idx);
		}
		if (size!=null) {
			dm.add("size", size);
		}
		dm.add("sort", "true");
		XmlDoc.Element r = executor().execute("om.pssd.collection.member.list", dm.root());

		// Iterate through SUbjects
		sb.append("DaRIS ID").append(",").append("first name").append(",").append("last name").append(",").append("sex").append(",").append("dob").append(",").append("FMP ID").append("\n");
		Collection<String> subjectIDs = r.values("object/id");
		for (String subjectID : subjectIDs) {
			PluginTask.checkIfThreadTaskAborted();

			// Fetch asset meta
			XmlDoc.Element oldSubjectMeta = AssetUtil.getAsset(executor(), subjectID, null);
			String oldSubjectName = oldSubjectMeta.value("asset/meta/daris:pssd-object/name");
			XmlDoc.Element oldDICOMMeta =  oldSubjectMeta.element("asset/meta/mf-dicom-patient");
			if (oldDICOMMeta==null) {
				throw new Exception ("THe DICOM meta-data on subject " + subjectID + " is missing");
			}
			w.push("subject");
			w.add("old-id", subjectID);
			w.add(oldDICOMMeta);


			// Now migrate the data for this Subject
			if (!listOnly) {
				migrateSubject (executor(), newProjectID, methodID, subjectID, oldSubjectName, oldDICOMMeta, cloneContent, copyRawContent, w);
			}
			w.pop();
		}


		// Write CSV file
		if (outputs!=null && outputs.size()==1) {
			String os = sb.toString();
			byte[] b = os.getBytes("UTF-8");
			output = outputs.output(0);
			output.setData(new ByteArrayInputStream(b), b.length, type);
		}

	}



	private void  migrateSubject (ServiceExecutor executor,   String newProjectID, String methodID, String oldSubjectID, 
			String oldSubjectName, XmlDoc.Element oldDICOMMeta,  Boolean cloneContent, Boolean copyRawContent, XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// FInd existing or create new Subject. We use mf-dicom-patient/{name,id} to find the Subject
		DICOMPatient dp = new DICOMPatient(oldDICOMMeta);	
		String newSubjectID = findOrCreateSubject (executor, methodID, oldSubjectID, dp, newProjectID);
		w.add("new-id", newSubjectID);
		// Find the new ExMethod (it's auto created when the Subject is made)
		Collection<String> newExMethodIDs = childrenIDs (executor, newSubjectID);
		if (newExMethodIDs.size()>1) {
			// This isn't possible as we only ever make one !
			// Need to check collection before hand
			throw new Exception ("Unhandled multiple number of ExMethods under Subject " + newSubjectID);
		}
		String newExMethodID = null;
		for (String t : newExMethodIDs) {
			newExMethodID = t;
		}


		// Now find the  Studies to migrate
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.push("sort");
		dm.add("key", "ctime");
		dm.pop();
		dm.add("size", "infinity");
		dm.add("where", "model='om.pssd.study' and cid starts with '" + oldSubjectID + "'");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		Collection<String> oldIDs = r.values("id");

		// No old Studies to migrate
		if (oldIDs==null) return;

		// Iterate through the Studies
		for (String oldID : oldIDs) {
			PluginTask.checkIfThreadTaskAborted();

			String oldStudyID = CiteableIdUtil.idToCid(executor, oldID);

			w.push("study");
			w.add("old-id", oldStudyID);

			// Find or create the new Study - dicom or raw
			String newStudyID = findOrCreateStudy (executor, oldSubjectName, oldStudyID, newExMethodID,  w);


			// Now  find extant or clone the DataSets
			findOrCloneDataSets (executor, oldStudyID, newStudyID, cloneContent, copyRawContent, w);


			// CHeck number of DataSets is correct
			int nIn = nChildren (executor, oldStudyID, "om.pssd.dataset");
			int nOut = nChildren (executor, newStudyID, "om.pssd.dataset");
			w.add("datasets-in", nIn);
			w.add("datasets-out", nOut);
			w.pop();
		}
	}




	private String findOrCreateStudy (ServiceExecutor executor, String oldSubjectName, String oldStudyID, 
			String newExMethodID,  XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// Fetch old meta-data
		XmlDoc.Element oldStudyMeta = AssetUtil.getAsset(executor, oldStudyID, null);
		XmlDoc.Element oldDICOM = oldStudyMeta.element("asset/meta/mf-dicom-study");
		XmlDoc.Element oldRaw = oldStudyMeta.element("asset/meta/daris:siemens-raw-mr-study");
		if (oldDICOM!=null && oldRaw!=null) {
			throw new Exception ("Study "+ oldStudyID + " holds both DICOM and Raw meta-data - this is not good");
		}
		Boolean isDICOM = (oldDICOM!=null);

		// See if new STudy pre-exists by study UID
		String newStudyID = null;
		Integer nChildren = nChildren (executor, newExMethodID, "om.pssd.study");
		if (nChildren!=0) {

			// It has some children - maybe the one we want.

			if (isDICOM) {
				String uid = oldDICOM.value("uid");

				// The old Study is DICOM.  Try and see if it already exists
				// in the migrated Project
				XmlDocMaker dm = new XmlDocMaker("args");
				dm.add("where", "cid starts with '" + newExMethodID + "' and model='om.pssd.study' and " +
						"xpath(mf-dicom-study/uid)='" + uid + "'");
				XmlDoc.Element r = executor.execute("asset.query", dm.root());
				Collection<String> ids = r.values("id");
				if (ids!=null) {
					if (ids.size()>1) {
						throw new Exception("Found multiple instances of DICOM Study with uid '"+uid+"' under ExMethod '"+newExMethodID+"'");
					}

					// Here is the good one. It may not have any DataSets...
					String t =  ids.iterator().next();	
					newStudyID = CiteableIdUtil.idToCid(executor, t);
					w.add("new-id", new String[]{"status", "found"}, newStudyID);
				}
			} else {
				String date = oldRaw.value("date");   
				String ingestDate = oldRaw.value("ingest/date");
				XmlDocMaker dm = new XmlDocMaker("args");
				String where =  "cid starts with '" + newExMethodID + "' and model='om.pssd.study' and " +
						" xpath(daris:siemens-raw-mr-study/ingest/date)='" + ingestDate  + "'";
				if (date!=null) {
					where += " and xpath(daris:siemens-raw-mr-study/date)='" + date + "'";
				}
				dm.add("where", where);
				XmlDoc.Element r = executor.execute("asset.query", dm.root());
				Collection<String> ids = r.values("id");
				if (ids!=null) {
					if (ids.size()>1) {
						throw new Exception("Found multiple instances of Raw Study with date '"+date+"' under ExMethod '"+newExMethodID+"'");
					}

					// Here is the good one. It may not have any DataSets...
					String t =  ids.iterator().next();	
					newStudyID = CiteableIdUtil.idToCid(executor, t);
					w.add("new-id", new String[]{"status", "found"}, newStudyID);
				}
			}
		} 

		// We didn't find it so make new Study
		PluginTask.checkIfThreadTaskAborted();
		if (newStudyID==null) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("pid", newExMethodID);
			dm.add("step", 1);                // It's always step 1 in the archive
			dm.add("fillin", "true");
			String oldName = oldStudyMeta.value("asset/meta/daris:pssd-object/name");
			String oldDescription = oldStudyMeta.value("asset/meta/daris:pssd-object/description");
			if (oldName!=null) {
				dm.add("name", oldName);
			}
			if (oldDescription!=null) {
				dm.add("description", oldDescription);
			}
			XmlDoc.Element r = executor.execute("om.pssd.study.create", dm.root());
			newStudyID = r.value("id");
			w.add("new-id", new String[]{"status", "created"}, newStudyID);

			// Copy the mf-dicom-study meta-data across
			String from = CiteableIdUtil.cidToId(executor, oldStudyID);
			String to = CiteableIdUtil.cidToId(executor, newStudyID);

			dm = new XmlDocMaker("args");
			dm.add("action", "add");
			dm.add("from", from);
			if (isDICOM) {
				dm.add("doc", new String[]{"ns", "dicom", "tag", "pssd.meta"}, "mf-dicom-study");
				dm.add("to", new String[]{"ns", "dicom", "tag", "pssd.meta"}, to);
			} else {
				dm.add("doc", new String[]{"ns", "om.pssd.study", "tag", "pssd.meta"}, "daris:siemens-raw-mr-study");
				dm.add("to", new String[]{"ns", "om.pssd.study", "tag", "pssd.meta"}, to);
			}
			executor.execute("nig.asset.doc.copy", dm.root());
		}
		//
		return newStudyID;
	}



	private String findOrCreateSubject (ServiceExecutor executor,  String methodID, String oldSubjectID, 
			DICOMPatient dp, String newProjectID) throws Throwable {

		// See if we can find the Subject pre-existing
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.push("sort");
		dm.add("key", "ctime");
		dm.pop();
		String where = "cid starts with '" + newProjectID + "' and model='om.pssd.subject' and ";

		// We don't always have a name.  If not, try ID
		Boolean ok = false;
		if (dp.getFirstName()!=null && dp.getLastName()!=null) {
			where += "xpath(mf-dicom-patient/name[@type='first'])='"+dp.getFirstName() + "' and ";
			where += "xpath(mf-dicom-patient/name[@type='last'])='"+dp.getLastName() + "'";	
			ok = true;
		} else if (dp.getID()!=null) {
			where += "xpath(mf-dicom-patient/id)='"+dp.getID() + "'";
			ok = true;
		}
		if (ok) {
			dm.add("where", where);
			XmlDoc.Element r = executor.execute("asset.query", dm.root());
			String newSubjectID = r.value("id");
			if (newSubjectID!=null) {
				// Return found Subject
				return CiteableIdUtil.idToCid(executor, newSubjectID);
			}
		}

		// Create a new Subject and ExMethod  
		dm = new XmlDocMaker("args");
		dm.add("pid", newProjectID);
		dm.add("method", methodID);	
		dm.add("fillin", true);
		String newSubjectName = dp.getID() + "-" + dp.getLastName();

		dm.add("name", newSubjectName);
		XmlDoc.Element r = executor.execute("om.pssd.subject.create", dm.root());
		String newSubjectID = r.value("id");

		// Now copy meta-data across
		String from = CiteableIdUtil.cidToId(executor, oldSubjectID);
		String to = CiteableIdUtil.cidToId(executor, newSubjectID);
		//
		dm = new XmlDocMaker("args");
		dm.add("action", "add");
		dm.add("from", from);
		dm.add("doc", "mf-dicom-patient");
		dm.add("to", new String[]{"ns", "pssd.private"}, to);
		executor.execute("nig.asset.doc.copy", dm.root());
		//
		return newSubjectID;
	}


	private void findOrCloneDataSets (ServiceExecutor executor, String oldStudyID, 
			String newStudyID, Boolean cloneContent, Boolean copyRawContent, XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// Find old DataSets
		Collection<String> oldDataSetIDs = childrenIDs (executor, oldStudyID);

		// Iterate and clone
		if (oldDataSetIDs!=null) {
			for (String oldDataSetID : oldDataSetIDs) {
				PluginTask.checkIfThreadTaskAborted();

				w.push("dataset");
				w.add("old-id", oldDataSetID);
				// Get old asset meta-data and UID
				XmlDoc.Element asset = AssetUtil.getAsset(executor, oldDataSetID, null);

				XmlDoc.Element oldDICOM = asset.element("asset/meta/mf-dicom-series");
				XmlDoc.Element oldRaw = asset.element("asset/meta/daris:siemens-raw-mr-series");
				if (oldDICOM!=null && oldRaw!=null) {
					throw new Exception ("DataSet "+ oldDataSetID + " holds both DICOM and Raw meta-data - this is not good");
				}
				Boolean isDICOM = (oldDICOM!=null);

				// See if we can find it the DataSet already migrated
				XmlDocMaker dm = new XmlDocMaker("args");
				if (isDICOM) {
					String uid = oldDICOM.value("uid");
					dm.add("where", "cid starts with '" + newStudyID + "' and model='om.pssd.dataset' and "+
							"xpath(mf-dicom-series/uid)='"+uid+"'");
				} else {
					String fn = asset.value("asset/meta/daris:pssd-filename/original");
					dm.add("where", "cid starts with '" + newStudyID + "' and model='om.pssd.dataset' and "+
							"xpath(daris:pssd-filename/original)='"+fn+"'");
				}
				XmlDoc.Element r = executor.execute("asset.query", dm.root());
				String id = r.value("id");
				if (id!=null) {
					w.add("new-id", new String[]{"status", "found"}, CiteableIdUtil.idToCid(executor, id));
				} else {

					// Create new by cloning it (coping all meta-data)
					dm = new XmlDocMaker("args");
					dm.add("id", oldDataSetID);
					dm.add("pid", newStudyID);
					dm.add("fillin", "true");
					if (isDICOM) {
						if (cloneContent) {
							dm.add("content", "true");
						} else {
							dm.add("content", "false");						
						}
						r = executor.execute("om.pssd.dataset.clone", dm.root());
						String newDataSetID = r.value("id");

						if (cloneContent) {
							w.add("new-id", new String[]{"status", "created", "content", "copied"}, newDataSetID);
						} else {
							w.add("new-id", new String[]{"status", "created", "content", "none"}, newDataSetID);
						}
					} else {
						if (cloneContent) {
							// If we clone the raw datset content, we may copy or move the content. 
							// If we move, it's a 2-step process
							if (copyRawContent) {
								dm.add("content", "true");
							} else {
								dm.add("content", "false");
							}
						} else {
							dm.add("content", "false");
						}
						r = executor.execute("om.pssd.dataset.clone", dm.root());		
						String newDataSetID = r.value("id");

						// Now move the content over as needed
						if (cloneContent) {
							if (!copyRawContent) {
								String contentURL = asset.value("asset/content/url");
								String contentType = asset.value("asset/content/type");
								if (contentURL!=null && contentType!=null) {
									setAssetContentUrlAndType (newDataSetID, contentURL, contentType);
									internalizeAssetByMove (newDataSetID);
									w.add("new-id", new String[]{"status", "created", "content", "moved"}, newDataSetID);
								} else {
									w.add("new-id", new String[]{"status", "created", "content", "failed"}, newDataSetID);
								}
							} else {
								w.add("new-id", new String[]{"status", "created", "content", "copied"}, newDataSetID);
							}
						} else {
							w.add("new-id", new String[]{"status", "created", "content", "none"}, newDataSetID);
						}
					}
				}
				w.pop();
			}
		}
	}



	private void setAssetContentUrlAndType(String cid, String contentUrl, String contentType) throws Throwable {

		// asset.set :cid $cid :url -by reference $url
		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("cid", cid);
		doc.add("url", new String[] { "by", "reference" }, contentUrl);
		doc.add("ctype", contentType);
		executor().execute("asset.set", doc.root());
	}


	private void internalizeAssetByMove (String cid) throws Throwable {

		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("cid", cid);
		doc.add("method", "move");
		executor().execute("asset.internalize", doc.root());
	}


	Integer nChildren(ServiceExecutor executor, String cid, String model) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("where", "cid starts with '" + cid + "' and model='"+model+"'");
		dm.add("action", "count");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		return Integer.parseInt(r.value("value"));
	}


	Collection<String> childrenIDs (ServiceExecutor executor, String cid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", cid);
		dm.add("sort", "true");
		XmlDoc.Element r = executor.execute("om.pssd.collection.member.list", dm.root());
		return r.values("object/id");
	}
}
