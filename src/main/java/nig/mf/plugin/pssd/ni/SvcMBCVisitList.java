package nig.mf.plugin.pssd.ni;

import java.io.ByteArrayInputStream;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Date;

import mbciu.commons.FMPAccess;
import mbciu.mbc.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
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


public class SvcMBCVisitList extends PluginService {


	private Interface _defn;
	private static final String FMP_CRED_REL_PATH = "/.fmp/mbc_migrate";
	private static final String OTHER_ID_TYPE = "Melbourne Brain Centre Imaging Unit";
	private static final String OTHER_ID_TYPE_LEGACY = "Melbourne Brain Centre Imaging Unit 7T Legacy";

	public SvcMBCVisitList() {
		_defn = new Interface();
		_defn.add(new Element("id", CiteableIdType.DEFAULT, "The citeable id of the Subject.", 1, 1));
		_defn.add(new Element("type", StringType.DEFAULT, "The visit type: 'MR' (default) or 'PETCT'", 0, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.visit.list";
	}

	public String description() {

		return "Find and list all FMP visits for this DaRIS Subject ID.";
	}

	public Interface definition() {

		return _defn;
	}

	public Access access() {

		return ACCESS_ADMINISTER;
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
		String id = args.stringValue("id");
		String type = args.stringValue("type", "MR");
		if (!type.equals("MR") && !type.equals("PETCT")) {
			throw new Exception("Type must be 'MR' or 'PETCT'");
		}

		// OPen FMP
		MBCFMP mbc = null;
		String home = System.getProperty("user.home");
		String resourceFile = FMP_CRED_REL_PATH;
		String path = home + resourceFile;
		try {
			w.push("FileMakerPro");
			mbc = new MBCFMP(path);
			FMPAccess fmp = mbc.getFMPAccess();
			w.add("ip", fmp.getHostIP());
			w.add("db", fmp.getDataBaseName());
			w.add("user", fmp.getUserName());
			w.pop();
		} catch (Throwable tt) {
			throw new Exception(
					"Failed to establish JDBC connection to FileMakerPro with resource file  '" + path + "'.", tt);
		}

		// Fetch the Subject and get the FMP ID
		XmlDoc.Element meta = AssetUtil.getAsset(executor(), id, null);
		String fmpID = meta.value("asset/meta/mf-dicom-patient/id");

		// Get all 7T Visits
		ResultSet rs = null;
		if (type.equals("MR")) {
			rs = mbc.get7TMRVisits(fmpID);
		} else {
			rs = mbc.getPETVisits(fmpID, null, null);
		}

		// List
		if (rs!=null) {
			rs.beforeFirst();
			while (rs.next()) {
				w.push("visit");
				String visitID = rs.getString("MRI7T_VISIT_IDENTIFIER_CALC");
				String date = rs.getString("MRI_DATE7T");
				String time = rs.getString("MRI_TIME7T");
				w.add("id", visitID);
				w.add("date", date);
				w.add("time", time);
				w.pop();
			}
		} else {
			w.add("visit", "not-found");
		}

		// Close up
		mbc.closeConnection();
	}
}
