package nig.mf.plugin.pssd.ni.Retired;

import java.util.Vector;
import java.util.Collection;

import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.PSSDUtil;

import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

/**
 * Specialized function to migrate meta-data from nig-daris:pssd-project and nig-daris:pssd-ethics into
 * new generic pssd defined document types
 * 
 * @author nebk
 * 
 */
public class SvcProjectMetaGenericMigrate extends PluginService {
	private Interface _defn;

	public SvcProjectMetaGenericMigrate() {

		_defn = new Interface();
		_defn.add(new Element("cid", CiteableIdType.DEFAULT,
				"The citeable asset id of the local Project to be migrated. If not set, Projects will be found and migrated.",
				0, 1));
	}

	public String name() {
		return "nig.pssd.project.meta.migrate";
	}

	public String description() {
		return "Specialized service to migrate nig-daris:pssd-{project,ethics} to daris:pssd-project-{research-category,governance}.";
	}

	public Interface definition() {
		return _defn;
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {

		// Get the ID of the specified Project if any
		String cid = args.value("cid");

		// Construct a List of Project asset IDs to loop over
		Collection<String> projects = null;
		if (cid == null) {
			// Find all Subjects
			XmlDocMaker doc = new XmlDocMaker("args");
			doc.add("where", "model='om.pssd.project' and (nig-daris:pssd-project has value or nig-daris:pssd-ethics has value)");
			doc.add("size", "infinity");
			doc.add("pdist", 0);          // Force local
			XmlDoc.Element r1 = executor().execute("asset.query", doc.root());
			projects = r1.values("id");
		} else {
			// Get this asset and verify it is a Subject; exception if not
			PSSDUtil.isValidProject(executor(), cid, true);
			projects = new Vector<String>();
			projects.add(AssetUtil.getId(executor(), cid));
		}

		// Iterate over all Projects
		for (String project : projects) {
			cid = AssetUtil.getCid(executor(), project);
			System.out.println("id,cid="+project + "," + cid);

			// Get the Subject Pathology meta-data
			if (PSSDUtil.isReplica(executor(), cid)) {
				w.add("project", "The given Project '" + cid + "' is a replica. Cannot modify it.");
			} else {

				// Get the asset
				XmlDoc.Element meta = AssetUtil.getAsset(executor(), null, project);

				// Populate daris:pssd-project-governance
				createGovernance (executor(), meta, project);

				// Populate daris:pssd-project-research-category
				createCategory (executor(), meta, project);

				// Clean up old documents
				cleanUp (executor(), project);

				w.add("project", cid);
			}
		}
	}



	private void cleanUp (ServiceExecutor executor, String id) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker ("args");
		dm.add("id", id);
		dm.add("type", "nig-daris:pssd-project");
		dm.add("type", "nig-daris:pssd-ethics");
		executor.execute("nig.asset.doc.remove", dm.root());
		
	}
	private void createGovernance (ServiceExecutor executor, XmlDoc.Element meta, String id) throws Throwable {

		XmlDoc.Element project = meta.element("asset/meta/nig-daris:pssd-project");
		XmlDoc.Element ethics = meta.element("asset/meta/nig-daris:pssd-ethics");

		if (project!=null || ethics!=null) {
			Collection<XmlDoc.Element> fundingIDs = null;
			Collection<XmlDoc.Element> facilityIDs = null;

			Collection<XmlDoc.Element> ethicsIDs = null;
			XmlDoc.Element retainMin = null;
			XmlDoc.Element retainMax = null;

			if (project!=null) {
				fundingIDs = project.elements("funding-id");
				facilityIDs = project.elements("facility-id");
			}
			if (ethics!=null) {
				ethicsIDs = ethics.elements("ethics-id");
				retainMin = ethics.element("retain-min");
				retainMax = ethics.element("retain-max");
			}

			if (fundingIDs!=null || ethicsIDs!=null || facilityIDs!=null || 
					retainMin!=null || retainMax!=null) {

				// Prepare
				XmlDocMaker dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push("meta");
				dm.push("daris:pssd-project-governance", new String[] { "ns", "pssd.public", "tag", "pssd.meta"});
				//
				addElements (dm, fundingIDs, null);
				addElements (dm, ethicsIDs, null);
				addElements (dm, facilityIDs, null);
				//
				if (retainMin!=null || retainMax!=null) {
					dm.push("data-retention");
					if (retainMin!=null) dm.add(retainMin);
					if (retainMax!=null) dm.add(retainMax);
					dm.pop();
				}
				//
				dm.pop();
				dm.pop();

				// Set
				executor().execute("asset.set", dm.root());

			}
		}
	}

	private void createCategory (ServiceExecutor executor, XmlDoc.Element meta, String id) throws Throwable {

		XmlDoc.Element project = meta.element("asset/meta/nig-daris:pssd-project");

		if (project!=null) {
			Collection<XmlDoc.Element> keywords = project.elements("keyword");
			Collection<XmlDoc.Element> fors = project.elements("field-of-research");

			if (keywords!=null || fors!=null) {

				// Prepare
				XmlDocMaker dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push("meta");
				dm.push("daris:pssd-project-research-category", new String[] { "ns", "pssd.public", "tag", "pssd.meta"});
				//
				addElements (dm, keywords, null);
				addElements (dm, fors, "ANZSRC-11");
				//
				dm.pop();
				dm.pop();

				// Set
				executor().execute("asset.set", dm.root());

			}
		}
	}


	private void addElements (XmlDocMaker dm, Collection<XmlDoc.Element> things, String newName) throws Throwable {
		if (things!=null) {
			for (XmlDoc.Element thing : things) {
				if (newName!=null) thing.setName(newName);
				dm.add(thing);
			}
		}

	}

}
