package nig.mf.plugin.pssd.ni;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import arc.mf.plugin.PluginLog;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;
import mbc.FMP.MBCFMP;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;

public class SvcMBCDoseUpload extends PluginService {
    // Relative location of resource file on server
    private static final String FMP_CRED_REL_PATH = "/.fmp/petct_fmpcheck";
    private static final String EMAIL = "williamsr@unimelb.edu.au";
    // private static final String EMAIL = "nkilleen@unimelb.edu.au";

    private Interface _defn;

    public SvcMBCDoseUpload() throws Throwable {

        _defn = new Interface();
        //

        Interface.Element me = new Interface.Element("cid",
                CiteableIdType.DEFAULT,
                "The citeable ID of the parent Study holding the SR DICOM modality DataSet.",
                0, 1);

        _defn.add(me);
        //
        me = new Interface.Element("id", AssetType.DEFAULT,
                "The asset ID (not citeable) of the parent Study holding the SR DICOM modality DataSet..",
                0, 1);
        _defn.add(me);
        //
        me = new Interface.Element("email", StringType.DEFAULT,
                "The destination email address to send a report to if an exception occurs. Over-rides any built in one.",
                0, 1);
        _defn.add(me);
        //
        me = new Interface.Element("update", BooleanType.DEFAULT,
                "Actually update FMP with the new values. Defaults to false.",
                0, 1);
        _defn.add(me);
        //
        me = new Interface.Element("force", BooleanType.DEFAULT,
                "Over-ride the meta-data on the Study indicating that this Study has already been checked, and check regardless. Defaults to false.",
                0, 1);
        _defn.add(me);

        _defn.add(new Interface.Element("no-email", BooleanType.DEFAULT,
                "Do not send email. Defaults to false.", 0, 1));
    }

    @Override
    public String name() {
        return "nig.pssd.mbic.dose.upload";
    }

    @Override
    public String description() {

        return "Extracts a dose report from an SR modality PSSD DataSet and uploads values to the MBC IU FileMakerPro server data base.";
    }

    @Override
    public Interface definition() {

        return _defn;
    }

    @Override
    public Access access() {

        return ACCESS_ADMINISTER;
    }

    @Override
    public boolean canBeAborted() {

        return false;
    }

    @Override
    public void execute(XmlDoc.Element args, Inputs in, Outputs out,
            XmlWriter w) throws Throwable {

        // Parse input ID
        Boolean updateFMP = args.booleanValue("update", false);
        String studyCID = args.value("cid");
        String studyID = args.value("id");
        String email = args.stringValue("email", EMAIL);
        Boolean force = args.booleanValue("force", false);
        boolean noEmail = args.booleanValue("no-email", false);
        if (noEmail) {
            email = null;
        }

        //
        int findMethod = 0; // Find by first Visit in FMP with DaRIS ID matching
                            // the Study

        // Check
        if (studyID == null && studyCID == null) {
            throw new Exception("Must supply 'id' or 'cid'");
        }
        if (studyCID == null)
            studyCID = CiteableIdUtil.idToCid(executor(), studyID);

        // Have we already processed the SR DataSet for this Study
        XmlDoc.Element studyMeta = AssetUtil.getAsset(executor(), studyCID,
                null);
        Boolean isProcessed = checked(executor(), studyMeta);
        if (force)
            isProcessed = false;
        if (isProcessed)
            return;

        // Do it
        try {
            update(executor(), updateFMP, studyCID, findMethod, w);

            // Indicate we have processed this study successfully
            if (updateFMP) {
                w.add("update-study-meta", true);
                setStudyMetaData(executor(), studyCID);
            }
        } catch (Throwable t) {
            String error = "nig.dicom.mbic.dose.upload : For Study '" + studyCID
                    + "' an error occured extracting (from DICOM) or setting (in FMP) the dose meta-data : "
                    + t.getMessage();

            // log the error
            PluginLog.log().add(PluginLog.ERROR, error, t);

            String msg;
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println(error);
            msg = sw.toString();
            pw.close();
            sw.close();

            // send notification email with error message and stack trace
            if (email != null) {
                SvcMBCPETVarCheck.send(executor(), email, msg);
            }

        }

    }

    private void update(ServiceExecutor executor, Boolean updateFMP,
            String studyCID, int findMethod, XmlWriter w) throws Throwable {

        // FInd SR DataSet. Nothing to do if none.
        String srCID = findSR(executor, studyCID);
        if (srCID == null) {
            return;
        }

        // CHeck Series type
        XmlDoc.Element seriesMeta = AssetUtil.getAsset(executor(), srCID, null);
        String modality = seriesMeta
                .value("asset/meta/mf-dicom-series/modality");
        if (modality == null) {
            throw new Exception("No DICOM modality found for DataSet " + srCID);
        }
        if (!modality.equals("SR")) {
            throw new Exception("Unexpected modality (" + modality
                    + ") for DataSet " + srCID);
        }

        // Extract the Dose report
        XmlDocMaker dm = new XmlDocMaker("args");
        dm.add("cid", srCID);
        XmlDoc.Element doseReport = executor().execute("daris.dicom.sr.get",
                dm.root());
        if (doseReport == null) {
            throw new Exception(
                    "Null dose report for DICOM SR DataSet " + srCID);
        }

        // Fetch the DICOM meta-data
        String id = CiteableIdUtil.cidToId(executor, srCID);
        dm = new XmlDocMaker("args");
        dm.add("id", id);
        XmlDoc.Element dicomMeta = executor().execute("dicom.metadata.get",
                dm.root());
        if (dicomMeta == null) {
            throw new Exception(
                    "Failed to extract DICOM meta-data from SR dataSet "
                            + srCID);
        }

        // Open FMP database with credential in server side resource file
        // holding
        // <dbname>,<ip>,<user>,<encoded pw>
        MBCFMP mbc = null;
        try {
            String t = System.getenv("HOME");
            String path = t + FMP_CRED_REL_PATH;
            mbc = new MBCFMP(path);
        } catch (Throwable tt) {
            throw new Exception(
                    "Failed to establish JDBC connection to FileMakerPro");
        }

        // Poke stuff in FMP
        try {
            updateFMP(findMethod, studyCID, mbc, dicomMeta, doseReport,
                    updateFMP, w);
        } catch (Throwable t) {
            mbc.closeConnection();
            throw new Exception(t);
        }

        // Close FMP
        mbc.closeConnection();
    }

    private String findSR(ServiceExecutor executor, String studyCID)
            throws Throwable {
        XmlDocMaker dm = new XmlDocMaker("args");
        String where = "cid starts with '" + studyCID
                + "' and xpath(mf-dicom-series/modality)='SR'";
        dm.add("pdist", "0");
        dm.add("where", where);
        XmlDoc.Element r = executor.execute("asset.query", dm.root());
        if (r == null)
            return null;

        Collection<String> ids = r.values("id");
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        if (ids.size() > 1) {
            throw new Exception(
                    "Found " + ids.size() + " SR modality DataSets under Study "
                            + studyCID + " - cannot handle");
        }
        String id = r.value("id");
        return CiteableIdUtil.idToCid(executor, id);
    }

    private void updateFMP(int findMethod, String studyCID, MBCFMP mbc,
            XmlDoc.Element dicomMeta, XmlDoc.Element doseReport, Boolean update,
            XmlWriter w) throws Throwable {

        // FInd the patient ID and study date from DICOM
        String mbcPatientID = getDicomValue(dicomMeta, "00100020");
        if (mbcPatientID == null) {
            throw new Exception(
                    "The patient ID is null in the DICOM meta-data");
        }
        w.add("MBC-patient-id", mbcPatientID);

        // Find the date
        String sdate = getDicomValue(dicomMeta, "00080021");
        if (sdate == null) {
            throw new Exception(
                    "The study date is null in the  DICOM meta-data");
        }
        w.add("date", sdate);
        Date date = DateUtil.dateFromString(sdate, "dd-MMM-yyyy");

        // Fetch the required values from the dose report and specifically
        // desired CT acquisition
        String t = doseReport.value(
                "dicom-structured-report/item/item[@name='CT Accumulated Dose Data']/item[@name='CT Dose Length Product Total']");
        if (t == null) {
            throw new Exception(
                    "Failed to extract doseLengthProductTotal from dose report.");
        }

        // CTDoseLengthProductTotal
        // Of the form "273.24 mGycm". FMP just stores the number
        String[] parts = t.split(" ");
        String doseLengthProductTotal = parts[0].trim();
        w.push("DICOM");
        w.add("doseLengthProductTotal", doseLengthProductTotal);

        // FInd the desired acquistion in the dose report
        XmlDoc.Element acq = findAcquisition(doseReport);
        if (acq == null) {
            throw new Exception(
                    "Failed to find the correct CT Acquisition in the dose report");
        }

        // XRay Modulation Type
        // :item -name "Comment" -relationship "CONTAINS" -code "121106" -type
        // "TEXT" "Internal technical scan parameters: Organ Characteristic =
        // Head, Body Size = Adult, Body Region = Head, X-ray Modulation Type =
        // Z_EC"
        String XRayModType = null;
        XmlDoc.Element commentEl = acq.element("item[@name='Comment']");
        String comment = commentEl.value();
        parts = comment.split(",");
        int n = parts.length;
        for (int i = 0; i < n; i++) {
            if (parts[i].contains("X-ray Modulation Type")) {
                String p = parts[i];
                int idx = p.indexOf("=");
                String pp = p.substring(idx + 1);
                String ppp = pp.trim();
                XRayModType = ppp;
            }
        }
        if (XRayModType == null) {
            throw new Exception(
                    "Failed to extract XRayModType from CT acquisition");
        }
        w.add("XRayModulationType", XRayModType);

        // DLP
        // :item -name "DLP" -relationship "CONTAINS" -code "113838" -type "NUM"
        // "269.77 mGycm"
        Double dlp = getDLP(acq);
        if (dlp == null) {
            throw new Exception("Failed to extract DLP from CT acquisition");
        }
        w.add("DLP", dlp);

        // kVP. FMP just stores the number
        // :item -name "KVP" -relationship "CONTAINS" -code "113733" -type "NUM"
        // "100 kV"
        XmlDoc.Element kVpEl = acq.element(
                "item[@name='CT Acquisition Parameters']/item[@name='CT X-Ray Source Parameters']/item[@name='KVP']");
        String kVpStr = kVpEl.value();
        parts = kVpStr.split(" ");
        String kVp = parts[0].trim();
        if (kVp == null) {
            throw new Exception("Failed to extract kVP from CT acquisition");
        }
        w.add("kVP", kVp);

        // XRay Tube Current
        // :item -name "X-Ray Tube Current" -relationship "CONTAINS" -code
        // "113734" -type "NUM" "72 mA"

        XmlDoc.Element currentEl = acq.element(
                "item[@name='CT Acquisition Parameters']/item[@name='CT X-Ray Source Parameters']/item[@name='X-Ray Tube Current']");
        String currentStr = currentEl.value();
        parts = currentStr.split(" ");
        String current = parts[0].trim();
        if (current == null) {
            throw new Exception(
                    "Failed to extract current from CT acquisition");
        }
        w.add("current", current);
        w.pop();

        // Now find the PET visits
        ResultSet petVisits = mbc.getPETVisits(mbcPatientID, null, date);

        // Number of visits
        int nVisits = SvcMBCPETVarCheck.numberVisits(petVisits);
        w.add("n-pet-visits", nVisits);

        // FInd the visit we want according to the desired find method
        int visitIdx = findVisit(petVisits, studyCID, findMethod);
        if (visitIdx == -1) {
            throw new Exception(
                    "Failed to find the correct Visit in FMP for this Study");
        }
        w.add("FMP-visit-index", visitIdx);

        // Iterate through PET visits
        petVisits.beforeFirst();
        int iVisit = 0;
        while (petVisits.next()) {
            if (iVisit == visitIdx) {
                // Get what's in FMP
                String fmpCTDoseLengthProductTotal = petVisits
                        .getString("DLPmGycmCT2");
                String fmpXRayModulationType = petVisits
                        .getString("XRayModulationType");
                String fmpDLP = petVisits.getString("DLPmGycmCT");
                String fmpkVp = petVisits.getString("kVp");
                String fmpXRayCurrent = petVisits.getString("ReferencemAs");
                w.push("FMP");
                w.add("doseLengthProductTotal", fmpCTDoseLengthProductTotal);
                w.add("XRayModulationType", fmpXRayModulationType);
                w.add("DLP", fmpDLP);
                w.add("kVP", fmpkVp);
                w.add("current", fmpXRayCurrent);
                w.pop();
                //
                // Update the dose fields in FMP
                if (update)
                    mbc.updateDose(mbcPatientID, date, doseLengthProductTotal,
                            XRayModType, "" + dlp, kVp, current, false);
            }
            iVisit++;
        }
    }

    /**
     * FInd the index of the visit for which the DaRIS ID is equal to the Study CID
     * Note that early version of SvcMBCPetVarCheck set the DataSet CID notr the Study
     * so account for that
     * 
     * @param rs
     * @param studyCID
     * @param findMethod
     *            0 : look for the first Visit for which the DaRIS ID is the CID
     *            of the Study we are handling
     * @return -1 if not found
     * @throws Throwable
     */
    private int findVisit(ResultSet rs, String studyCID, int findMethod)
            throws Throwable {
        rs.beforeFirst();
        int n = 0;
        while (rs.next()) {
            String id = rs.getString("DARIS_ID");
            int d = nig.mf.pssd.CiteableIdUtil.getIdDepth(id);
            int sd = CiteableIdUtil.studyDepth();
            
            // If we have a DataSet CID in FMP, find the Study CID (as that's
            // what we are setting now)
            String id2 = id;
            if (d>sd) {
            	id2 = nig.mf.pssd.CiteableIdUtil.getStudyId(id);
            }

            if (findMethod == 0) {
                if (id2.equals(studyCID))
                    return n;
            } else {
            	throw new Exception ("Unhandled visit find method");
            }
            n++;
        }
        return -1;

    }

    private String getDicomValue(XmlDoc.Element dicomMeta, String tag)
            throws Throwable {
        XmlDoc.Element el = dicomMeta.element("de[@tag='" + tag + "']");
        return el.value("value");
    }

    private XmlDoc.Element findAcquisition(XmlDoc.Element doseReport)
            throws Throwable {

        // dicom-structured-report
        // :item -name "X-Ray Radiation Dose Report"
        // :item -name "CT Acquisition"
        // :item -name "Acquisition Protocol" -relationship "CONTAINS" -code
        // "125203" -type "TEXT" "Topogram"
        // FInd first all the CT Acquisition elements that we want (the ones
        // without TOPOGRAM)

        Collection<XmlDoc.Element> els = doseReport.elements(
                "dicom-structured-report/item[@name='X-Ray Radiation Dose Report']/item[@name='CT Acquisition']");
        if (els == null) {
            throw new Exception(
                    "Could not extract any CT Acquisition elements from dose report");
        }
        ArrayList<XmlDoc.Element> acqs = new ArrayList<XmlDoc.Element>();
        for (XmlDoc.Element el : els) {
            XmlDoc.Element t = el.element("item[@name='Acquisition Protocol']");
            String value = t.value("@type");
            if (!value.equalsIgnoreCase("TOPOGRAM")) {
                acqs.add(el);
            }
        }

        // Now we have the list of CT acquisitions. FInd the one with the
        // largest DLP
        int n = acqs.size();
        if (n == 0) {
            throw new Exception(
                    "Could not extract any non-TOPOGRAM CT Acquisition elements from dose report");
        }
        int idx = 0;
        Double dlpMax = -1.0;
        for (int i = 0; i < n; i++) {
            XmlDoc.Element acq = acqs.get(i);
            Double dlp = getDLP(acq);
            if (dlp > dlpMax) {
                dlpMax = dlp;
                idx = i;
            }
        }

        // OK now we have the index for the acquisition with the largest value
        // of DLP
        // that's what we hand back
        return acqs.get(idx);
    }

    private Double getDLP(XmlDoc.Element acq) throws Throwable {
        XmlDoc.Element dose = acq.element("item[@name='CT Dose']");
        XmlDoc.Element dlp = dose.element("item[@name='DLP']");

        // Of the form -type "NUM" "269.77 mGycm"
        String dlpStr = dlp.value();
        String[] parts = dlpStr.split(" ");
        Double value = Double.parseDouble(parts[0]);
        return value;
    }

    private Boolean checked(ServiceExecutor executor, XmlDoc.Element studyMeta)
            throws Throwable {

        XmlDoc.Element meta = studyMeta
                .element("asset/meta/nig-daris:pssd-mbic-fmp-check");
        if (meta == null)
            return false;

        XmlDoc.Element t = meta.element("dose");
        if (t == null)
            return false;
        if (t.booleanValue() == true)
            return true;

        return false;
    }

    private void setStudyMetaData(ServiceExecutor executor, String studyCID)
            throws Throwable {

        // Set meta-data on the Study indicating the PET or CT checking has been
        // done
        XmlDocMaker dm = new XmlDocMaker("args");
        dm.add("id", studyCID);
        dm.push("meta", new String[] { "action", "merge" });
        dm.push("nig-daris:pssd-mbic-fmp-check");
        dm.add("dose", true);
        dm.pop();
        dm.pop();
        executor.execute("om.pssd.study.update", dm.root());
    }

}