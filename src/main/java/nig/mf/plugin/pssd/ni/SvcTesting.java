package nig.mf.plugin.pssd.ni;

import java.io.File;
import java.util.Collection;

import nig.compress.ArchiveUtil;
import nig.mf.MimeTypes;

import arc.mf.plugin.PluginService;

import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlWriter;
import arc.xml.XmlDoc.Element;

public class SvcTesting extends PluginService {

	private Interface _defn;

	public SvcTesting()  {
		_defn = new Interface();		
		_defn.add(new Interface.Element("file",StringType.DEFAULT, "The citable identifier of the ExMethod", 1, 1));
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
		return "nig.test";
	}

	public void execute(Element args, Inputs inputs, Outputs outputs, XmlWriter w) throws Throwable {
		// Inputs
		
		String in = args.value("file");
		File f = new File(in);
		Collection<File> files = org.apache.commons.io.FileUtils.listFiles(f, null, true);
		files.add(new File(in+"/Empty"));
		for (File file : files ) {
			System.out.println("f=" + file.getAbsolutePath());
		}
		File t =  File.createTempFile("nig_pv_upload", ".aar");
		System.out.println("Archive = " + t.getAbsolutePath());
		ArchiveUtil.compress(files, f, t, MimeTypes.AAR, 0, null);  
	}
}

