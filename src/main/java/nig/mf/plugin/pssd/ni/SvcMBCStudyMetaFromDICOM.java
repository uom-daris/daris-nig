package nig.mf.plugin.pssd.ni;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.Date;
import java.util.Vector;

import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginLog;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.IntegerType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

public class SvcMBCStudyMetaFromDICOM extends PluginService {
	private Interface _defn;

	public SvcMBCStudyMetaFromDICOM() throws Throwable {

		_defn = new Interface();
		Interface.Element me = new Interface.Element("cid",
				CiteableIdType.DEFAULT,
				"The identity of the Study.", 0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("id", AssetType.DEFAULT,
				"The asset identity (not citable ID) of the Study.", 0,
				1);
		_defn.add(me);

	}

	@Override
	public String name() {
		return "nig.pssd.mbic.study.metadata.set";
	}

	@Override
	public String description() {
		return "Fetches the AccessionNumber from DICOM header and locates in daris:pssd-study/other-id";
	}

	@Override
	public Interface definition() {

		return _defn;
	}

	@Override
	public Access access() {

		return ACCESS_ACCESS;
	}

	@Override
	public boolean canBeAborted() {

		return false;
	}

	@Override
	public void execute(XmlDoc.Element args, Inputs in, Outputs out,
			XmlWriter w) throws Throwable {

		// Parse input ID
		String studyID = args.value("id");
		String studyCID = args.value("cid");
		if (studyID != null && studyCID != null) {
			throw new Exception("Can't supply 'id' and 'cid'");
		}
		if (studyID == null && studyCID == null) {
			throw new Exception("Must supply 'id' or 'cid'");
		}
		if (studyCID == null) {
			studyCID = CiteableIdUtil.idToCid(executor(), studyID);
		}
		
		// Fetch the meta-data from the first child DICOM dataset
		XmlDocMaker dm = new XmlDocMaker("args");
		String where = "cid starts with '" + studyCID + "' and " +
		               "model='om.pssd.dataset' and type='dicom/series'";
		dm.add("where", where);
		dm.add("size", "1");
		dm.add("pipe-generate-result-xml", "true");
		dm.add("action", "pipe");
		dm.add("service", new String[]{"name", "dicom.metadata.get"});	
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return;
		//
		String accessionNumber = r.value("de[@tag='00080050']/value");
		if (accessionNumber!=null) {
			dm = new XmlDocMaker("args");
			dm.add("id", studyCID);
			dm.add("other-id", accessionNumber);
			executor().execute("om.pssd.study.update", dm.root());
		}
	}
}