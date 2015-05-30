package nig.mf.plugin.pssd.ni.Retired;

import java.util.ArrayList;
import java.util.Collection;


import arc.mf.plugin.PluginService;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;
import arc.xml.XmlDoc.Element;

public class SvcMBCSeriesSourceSet extends PluginService {


	private Interface _defn;

	public SvcMBCSeriesSourceSet()  {
		_defn = new Interface();	
		_defn.add(new Interface.Element("id", AssetType.DEFAULT,
				"The asset ID of the raw Siemens Series asset to be updated.", 0, 1));
		_defn.add(new Interface.Element("where", StringType.DEFAULT,
				"Further query restriction.", 0, 1));

	}

	public Access access() {
		return ACCESS_MODIFY;
	}

	public Interface definition() {
		return _defn;
	}

	public String description() {
		return "Service to look for MBC imaging Unit Series assets and populate nig-siemens-raw-petct-serie/source from mf-revision-histtory/source (asset version 1).";
	}

	public String name() {
		return "nig.dicom.mbc.series.source.set";
	}

	public void execute(Element args, Inputs inputs, Outputs outputs, XmlWriter w) throws Throwable {

		// Find assets
		String assetID = args.value("id");
		Collection<String> ids = null;
		if (assetID!=null) {
			ids = new ArrayList<String>();
			ids.add(assetID);
		} else {

			
			// FInd all series with source not set
			String where = "(daris:siemens-raw-petct-series has value) and (xpath(daris:siemens-raw-petct-series /source) hasno value)";

			// Further qualification
			String where2 = args.value("where");
			if (where2!=null) {
				where += " and (" + where2 + ")";	
			}
			
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("pdist", 0);
			dm.add("size", "infinity");
			dm.add("where", where);
			XmlDoc.Element r = executor().execute("asset.query", dm.root());

			ids = r.values("id");
			if (ids==null || ids.size()==0) return;
		}
		
		// Iterate
		for (String id : ids) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("id", new String[]{"version", "1"}, id);
			XmlDoc.Element r2 = executor().execute("asset.get", dm.root());
			//
			String source = r2.value("asset/meta/mf-revision-history/source");
			if (source==null) {
				w.add("id", new String[]{"set", "false"}, id);
			} else {

				// Fish out file part from original file name.
				// Assuming Unix file types with "/" separator
				String[] parts = source.split("/");
				int n = parts.length;
				String fileName = parts[n-1];

				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push("meta", new String[]{"action", "merge"});
				dm.push("daris:siemens-raw-petct-series ");
				dm.add("source", fileName);
				dm.pop();
				dm.pop();

				// Set it
				executor().execute("asset.set", dm.root());
				w.add("id", new String[]{"set", "true"}, id);
			}
		}
	}
}