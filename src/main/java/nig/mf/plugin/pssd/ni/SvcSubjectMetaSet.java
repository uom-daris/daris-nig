package nig.mf.plugin.pssd.ni;


import arc.mf.plugin.*;
import arc.mf.plugin.dtype.*;
import arc.xml.*;

import nig.mf.plugin.pssd.util.ni.NIGDomainMetaData;
import nig.mf.plugin.util.PluginExecutor;
import nig.mf.Executor;

/**
 * This class is not intended as an end-user service. It's purpose is to convert meta-data from
 * objects in the Java layer to XML to be then handed to a service, which will in turn reconstitute
 * the original object.  It is used by the DICOM server (see PSSDStudyProxy)  and the Bruker client
 * to add meta-data to research-domain specific document types (from the nig package).  
 * 
 * This service is specified in a DICOM control that it should be called.  In this way we only 
 * loosely and configurably couple the DICOM server and Bruker client to domain specific packages like nig-pssd
 * 
 * @author nebk
 *
 */
public class SvcSubjectMetaSet extends PluginService {


	private Interface _defn;

	public SvcSubjectMetaSet() {
		
		// The meta-data in the dicom and bruker elements must match that expected by nig.iio.dicom.StudyMetadata.createFrom (XmlDoc.Element) 
		// because this containers are going to be reconstructed (also by StudyMetadata) from the XML supplied here.
		_defn = new Interface();		
		_defn.add(new Interface.Element("id",CiteableIdType.DEFAULT, "The citable identifier of the local Subject object. ", 1, 1));
		_defn.add(new Interface.Element("remove",BooleanType.DEFAULT, "Rather than setting meta-data, remove all instances of mapped elements in existing documents. You just have to supply 'dicom' and/or 'bruker' (but no children) to provide the context.", 0, 1));

		// DICOM
		Interface.Element me = new Interface.Element("dicom",XmlDocType.DEFAULT, "The DICOM meta-data (defined by StudyMetadata.toXML()", 0, 1);
		me.add(new Interface.Element("uid",StringType.DEFAULT,"Global unique identifier of Study. DICOM element (0020,000D)",0,1));
		me.add(new Interface.Element("id",StringType.DEFAULT,"Operator supplied Study identifier. DICOM element (0020,0010)",0,1));
		me.add(new Interface.Element("description",StringType.DEFAULT,"Description. Derived from DICOM element (0008,1030).",0,1));
		me.add(new Interface.Element("protocol",StringType.DEFAULT,"Protocol name. Derived from DICOM element (0018,1030).",0,1));
		me.add(new Interface.Element("accession_number",StringType.DEFAULT,"Accession Number. Derived from DICOM element (0080,0050).",0,1));
		me.add(new Interface.Element("date",DateType.DEFAULT,"Date and time (dd-MMM-yyyy HH:mm:ss) on which acquisition of the study was started. Derived from DICOM elements (0008,0020) and (0008,0030).",0,1));
		me.add(new Interface.Element("modality",StringType.DEFAULT,"Type of series (e.g. MR - Magnetic Resonance). Derived from DICOM element (0008,0060)",0,1));
		me.add(new Interface.Element("rpn",StringType.DEFAULT,"Referring Physician's Name. Derived from DICOM element (0008,0090)",0,1));
		me.add(new Interface.Element("institution",StringType.DEFAULT,"Name of the institution. Derived from DICOM element (0008,0080).",0,1));
		me.add(new Interface.Element("station",StringType.DEFAULT,"Name of the station. Derived from DICOM element (0008,1010).",0,1));
		me.add(new Interface.Element("manufacturer",StringType.DEFAULT,"Equipment manufacturer. Derived from DICOM element (0008,0070)",0,1));	
		me.add(new Interface.Element("model",StringType.DEFAULT,"Equipment model. Derived from DICOM element (0008,1090)",0,1));	
		me.add(new Interface.Element("magnetic_field_strength",StringType.DEFAULT,"Magnetic field. Derived from DICOM element (0018,0087)",0,1));	

		// SUbject sub-category
		Interface.Element mes = new Interface.Element("subject",XmlDocType.DEFAULT, "Subject-specific meta-data", 0, 1);
		mes.add(new Interface.Element("id",StringType.DEFAULT,"Patient's primary identification. From DICOM element (0010,0020).",0,1));	
		mes.add(new Interface.Element("name",StringType.DEFAULT,"Patient's full name. Derived from DICOM element (0010,0010)",0,1));	
		mes.add(new Interface.Element("dob",DateType.DEFAULT,"Patient's date of birth. Derived from DICOM elements (0010,0030) and (0010,0032).",0,1));
		mes.add(new Interface.Element("sex",StringType.DEFAULT,"Patient's sex. Derived from DICOM element (0010,0040).",0,1));	
		mes.add(new Interface.Element("age",DoubleType.DEFAULT,"Patient's age in years. Derived from DICOM element (0x0010,0x1010).",0,1));	
		mes.add(new Interface.Element("weight",DoubleType.DEFAULT,"Patient's weight in Kilograms.Derived from DICOM element (0010,1030).",0,1));	
		mes.add(new Interface.Element("size",DoubleType.DEFAULT,"Patient's height in metres. Derived from DICOM element (0010,1020).",0,1));	
		// 
		me.add(mes);
		_defn.add(me);
			
		// Bruker
		me = new Interface.Element("bruker",XmlDocType.DEFAULT, "The Bruker subject identifier meta-data (defined by NIGBrukerIdentifierMetaData.toXML()", 0, 1);
		me.add(new Interface.Element("project_descriptor",StringType.DEFAULT,"Project descriptor.",0,1));
		me.add(new Interface.Element("coil",StringType.DEFAULT,"Scanner coil.",0,1));
		me.add(new Interface.Element("animal_id",StringType.DEFAULT,"Animal identifier",0,1));
		me.add(new Interface.Element("date",DateType.DEFAULT,"Date of acquisition.",0,1));
		me.add(new Interface.Element("gender",StringType.DEFAULT,"Gender of subject.",0,1));
		me.add(new Interface.Element("exp_group",StringType.DEFAULT,"Experimental group.",0,1));
		me.add(new Interface.Element("vivo",StringType.DEFAULT,"In/ex vivo",0,1));
		_defn.add(me);
	}


	public String name() {
		return "nig.pssd.subject.meta.set";
	}

	public String description() {
		return "Add specific DICOM and/or Bruker subject identifier meta-data elements to PSSD objects (Project, Subject and Study) object via NIG-PSSD specific Document Types. Only operates on local objects.";
	}

	public Interface definition() {
		return _defn;
	}

	public Access access() {
		return ACCESS_MODIFY;
	}

	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {
		String id = args.value("id");
		Boolean remove = args.booleanValue("remove", false);
		Executor exec = new PluginExecutor(executor());
		if (args.element("dicom") != null || args.element("bruker") != null) {

			// Hand on to the framework to update what it can on the PSSD  objects
			// in the nig-pssd specific Document Types
			NIGDomainMetaData nmd = new NIGDomainMetaData();
			if (remove) {
				nmd.removeObjectMetaData(exec, id, args);
			} else {
				nmd.addObjectMetaData(exec, id, args);
			}
		} else {
			throw new Exception ("You must supply at least one of the arguments 'dicom' or 'bruker'");
		}
	}
}

