package nig.mf.plugin.pssd.ni;

import java.io.File;
import java.util.Collection;

import nig.compress.ArchiveUtil;
import nig.iio.metadata.XMLUtil;
import nig.mf.MimeTypes;
import nig.mf.plugin.util.AssetUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;
import arc.xml.XmlDoc.Element;

public class SvcHoffmanMigrate extends PluginService {

	private Interface _defn;
	final private static String META_NS = "proj-hoffmann_data-1128.4.49";
	final private static String META_DT_OUT = "individuals_assays";
	final private static String META_DT_KD = "individuals_KDR_assay";
	final private static String META_DT_WOL = "individuals_wolbachia_assay";

	public SvcHoffmanMigrate()  {
		_defn = new Interface();		
		_defn.add(new Interface.Element("namespace",StringType.DEFAULT, "The parent namespace.", 1, 1));
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public Interface definition() {
		return _defn;
	}

	public String description() {
		return "Test service";
	}

	public String name() {
		return "nig.hoffman.assay.migrate";
	}

	public void execute(Element args, Inputs inputs, Outputs outputs, XmlWriter w) throws Throwable {
		// Inputs

		String dtk = META_NS + ":" + META_DT_KD;
		String dtw = META_NS + ":" + META_DT_WOL;
		
		String assetNS = args.stringValue("namespace", META_NS);
		XmlDocMaker dm = new XmlDocMaker("args");
		String where = "namespace>='" + assetNS + "' and (xpath("+  dtk + ") has value or xpath("+dtw + ") has value)";
		dm.add("where", where);
		dm.add("pdist", 0);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		Collection<String> ids = r.values("id");
		if (ids==null) return;
		//
		String dt = META_NS + ":" + META_DT_OUT;
		for (String id : ids) {
			XmlDoc.Element asset = AssetUtil.getAsset(executor(), null, id);
			XmlDoc.Element kdr = asset.element("asset/meta/"+dtk);
			XmlDoc.Element wol = asset.element("asset/meta/"+dtw);
			if (kdr!=null || wol!=null) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.add("allow-invalid-meta", true);;
				dm.push("meta");
				dm.push(dt);
				if (kdr!=null) {
					dm.push("KDR_assay");
					dm.addAll(kdr.elements());
					dm.pop();
				}
				if (wol!=null) {
					dm.push("wolbachia_assay");
					dm.addAll(wol.elements());
					dm.pop();
				}
				dm.pop();
				dm.pop();
				//
				executor().execute("asset.set", dm.root());
				w.add("id", id);
				// Manual removal after of old doc types
			}
		}
	}
	
	public void removeAttribute (XmlDoc.Element doc, String attributeName) throws Throwable {
		XmlDoc.Attribute attr = doc.attribute(attributeName);
		if (attr != null) {
			doc.remove(attr);
		}

	}
}

