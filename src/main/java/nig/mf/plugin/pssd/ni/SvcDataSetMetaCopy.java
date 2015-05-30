package nig.mf.plugin.pssd.ni;

import java.util.Collection;

import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

// Specialized service used to fix up some meta-data after a migration

public class SvcDataSetMetaCopy extends PluginService {

	private Interface _defn;

	public SvcDataSetMetaCopy() {
		_defn = new Interface();
		_defn.add(new Element("pid", CiteableIdType.DEFAULT,
				"The citeable id of the parent PSSD object (repository, project, subject etc) to begin searching from.", 1, 1));
	}

	public String name() {
		return "nig.pssd.dataset.meta.copy";
	}

	public String description() {
		return "Specialized service that copies meta-data from one document type to another.  Currently daris:siemens-raw-petct-series /source to daris:pssd-filename.";
	}

	public Interface definition() {
		return _defn;
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {
		// Distributed ID for  DataSet. It must be a primary or we are not allowed
		// to modify it
		String cidIn = args.value("pid");
		

		// Query to find content
		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("where", "xpath(daris:pssd-object/type)='dataset' and " + "(cid starts with " + "'" + cidIn + "'"
				+ " or cid = " + "'" + cidIn + "') and "
				+ "xpath(daris:siemens-raw-petct-series /source) has value and rid hasno value");
		doc.add("size", "infinity");
		doc.add("pdist", 0);      // Force local
		XmlDoc.Element ret = executor().execute("asset.query", doc.root());

		// Get collection of asset IDs
		Collection<String> assets = ret.values("id");
		if (assets != null) {

			// Iterate through and get each asset
			for (String id : assets) {
				XmlDocMaker doc2 = new XmlDocMaker("args");
				doc2.add("id", id);
				XmlDoc.Element ret2 = executor().execute("asset.get", doc2.root());

				// Get the things we need
				String fileName = ret2.value("asset/meta/daris:siemens-raw-petct-series /source");
				String cid = ret2.value("asset/cid");

				// Update the PSSD dataset object
				XmlDocMaker doc3 = new XmlDocMaker("args");
				doc3.add("id", cid);
				doc3.add("filename", new String[]{"private", "true"}, fileName);
				doc3.pop();
				executor().execute("om.pssd.dataset.primary.update", doc3.root());
			}
		}
	}
}
