package nig.mf.plugin.pssd.ni;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import nig.mf.plugin.util.AssetUtil;
import nig.mf.plugin.util.DictionaryUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

public class SvcProjectMBCMigrate extends PluginService {

	private Interface _defn;

	public SvcProjectMBCMigrate() {

		// matches DocType daris:pssd-repository-description

		_defn = new Interface();
		_defn.add(new Element("id", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate.", 1, 1));
	}

	public String name() {

		return "nig.pssd.project.mbc.migrate";
	}

	public String description() {

		return "Migrates an MBC DaRIS project from visit-based Subject IDs to subject-based subject IDs.";
	}

	public Interface definition() {

		return _defn;
	}

	public Access access() {

		return ACCESS_MODIFY;
	}



	public void execute(XmlDoc.Element args, Inputs inputs, Outputs outputs, XmlWriter w)
	throws Throwable {

		// Parse arguments
		String oldProjectID = args.stringValue("id");
		XmlDoc.Element projectMeta = AssetUtil.getAsset(executor(), oldProjectID, null);
		String methodID = projectMeta.value("asset/meta/daris:pssd-project/method/id");

		// Fetch the visit-based Subjects
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", oldProjectID);
		XmlDoc.Element r = executor().execute("om.pssd.collection.member.list", dm.root());
		
		// Iterate through SUbjects
		Collection<String> subjectIDs = r.values("object/id");
		for (String subjectID : subjectIDs) {
			
			// Fetch asset meta
			XmlDoc.Element meta = AssetUtil.getAsset(executor(), subjectID, null);
			XmlDoc.Element dicomMeta =  meta.element("asset/meta/mf-dicom-patient");
			
			// Try to lookup in FMP
			String fmpID = findInFMP (executor(), dicomMeta);
			
			w.push("subject");
			w.add(dicomMeta);
			if (fmpID!=null) {
				w.add("found", "true");
				w.add("fmp-ID", fmpID);
			} else {
				w.add("found", "false");

				// Create new Patient Record in FMP
			}
			w.pop();
			
			
			// Now migrate the data
			String newProjectID = oldProjectID;
			String newSubjectID = migrateSubject (executor(), newProjectID, methodID, subjectID, fmpID);
		}

	}

	private String findInFMP (ServiceExecutor executor, XmlDoc.Element dicomMeta) throws Throwable {
		return null;
	}
	
	private String migrateSubject (ServiceExecutor executor,  String newProjectID, String methodID, String oldSubjectID, String fmpID) throws Throwable {
		// Create the new Subject and ExMethod 
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pid", newProjectID);
		dm.add("method", methodID);	
		XmlDoc.Element r = executor.execute("om.pssd.subject.create", dm.root());
		String newSubjectID = r.value("id");
		String newExMethodID = r.value("id/@mid");
		
		// Copy over meta-data from old to new
		// use nig.asset.doc.copy
		
		// Now find the old Studies
		dm = new XmlDocMaker("args");
		dm.add("id", oldSubjectID);
		dm.add("where", "model='om.pssd.study' and cid starts with '" + oldSubjectID + "'");
		r = executor.execute("asset.query");
		Collection<String> oldStudyIDs = r.values("id");
		
		// Iterate through Studies
		for (String oldStudyID : oldStudyIDs) {
			// Generate new Study
			dm = new XmlDocMaker("args");
			dm.add("pid", newExMethodID);
			
			// Copy over meta-data from old Study
			
			// Now  clone (copy) the DataSets

			
		}
		
		return newSubjectID;
		
	}
}
