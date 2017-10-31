package nig.mf.plugin.pssd.ni;

import java.io.File;
import java.util.Collection;

import mbciu.commons.Base64Coder;
import nig.compress.ArchiveUtil;
import nig.mf.MimeTypes;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlWriter;
import arc.xml.XmlDoc.Element;

public class SvcBase64 extends PluginService {

	private Interface _defn;

	public SvcBase64()  {
		_defn = new Interface();		
		_defn.add(new Interface.Element("string",StringType.DEFAULT, "The string. Make sure encloded in double quotes (if special characters).", 1, 1));
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public Interface definition() {
		return _defn;
	}

	public String description() {
		return "Encode and decode (base64) a String";
	}

	public String name() {
		return "nig.base64";
	}

	public void execute(Element args, Inputs inputs, Outputs outputs, XmlWriter w) throws Throwable {
		// Inputs

		String s = args.value("string");
		String et = Base64Coder.encodeString(s);
		w.add("in",s);
		w.push("encoded");
		w.add("encoded", et);
		w.add("decoded", Base64Coder.decodeString(et));
		w.pop();
	}
}

