package nig.mf.plugin.pssd.ni;


import java.util.Collection;

import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

public class SvcMBCMRStudyStationNameSet extends PluginService {

	private static final String STATION_NAME = "MRC18978";
	private static final String  INSTITUTION = "University of Melbourne - Brain Institute";
	private static final String MANUFACTURER = "SIEMENS";

	//
	private Interface _defn;

	public SvcMBCMRStudyStationNameSet() throws Throwable {

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
		return "nig.pssd.mbic.mr.station-name.set";
	}

	@Override
	public String description() {
		return "If the 7T scanner station name is missing, set (MRC18978) on the Study and child DataSets";
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

		// Have a look at the STudy
		XmlDoc.Element asset = AssetUtil.getAsset(executor(), studyCID, null);
		String model = asset.value("asset/model");
		if (!model.equals("om.pssd.study")) {
			throw new Exception ("The supplied object is not a Study - model = '" + model + "'");		
		}
		XmlDoc.Element dicom = asset.element("asset/meta/mf-dicom-study");
		if (dicom==null) {
			throw new Exception ("The supplied Study is not a DICOM study");
		}

		String stationName = dicom.value("location/station");
		w.add("station-name", stationName);
		if (stationName!=null) {
			return;  // Assume correct
		}

		w.push("study");
		w.add("id", studyCID);
		//Look at some other things to convince ourselves it's MBC data
		// In the use case being targeted, the pther meta-data is available.
		String institution = dicom.value("location/institution");
		String manufacturer = dicom.value("equipment/manufacturer");
		if (!institution.equals(INSTITUTION) || !manufacturer.equals(MANUFACTURER)) {
			w.add("dicom-meta-match", new String[]{"institution", institution, "manufacturer", manufacturer}, "false");
			w.pop();
			return;
		}
		w.add("dicom-meta-match", new String[]{"institution", institution, "manufacturer", manufacturer}, "true");

		// Set on the Study first
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", studyCID);
		dm.push("meta");
		dm.push("mf-dicom-study", new String[]{"ns", "dicom", "tag", "pssd.meta"});
		dm.push("location");
		dm.add("station",STATION_NAME);
		dm.pop();
		dm.pop();
		dm.pop();
		w.add(dm.root());
		executor().execute("asset.set", dm.root());

		// Now find the child DataSets
		dm = new XmlDocMaker("args");
		dm.add("size", "infinity");
		dm.add("action", "get-cid");
		dm.add("where", "cid starts with '" + studyCID + "' and model='om.pssd.dataset' and mf-dicom-series has value");
		dm.add("pdist", "0");
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		Collection<String> dataSetCIDs = r.values("cid");
		if (dataSetCIDs==null) {
			w.pop();
			return;
		}
		//
		w.push("data-set");
		for (String dataSetCID : dataSetCIDs) {
			setStationName (executor(), dataSetCID, STATION_NAME, w);
		}
		w.pop();
		w.pop();
	}


	private void setStationName (ServiceExecutor executor, String cid, String station, XmlWriter w) throws Throwable {
		// Set
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", cid);
		dm.push("element", new String[]{"action", "merge", "tag", "00081010"});
		dm.add("value", STATION_NAME);
		dm.pop();
		executor.execute("daris.dicom.metadata.set", dm.root());
		
		// prune asset
		String id = CiteableIdUtil.cidToId(executor, cid);
		dm = new XmlDocMaker ("args");
		dm.add("id", id);
		executor.execute("asset.prune", dm.root());
		w.add("id", cid);
	}

}