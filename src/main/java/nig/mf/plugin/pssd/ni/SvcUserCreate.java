package nig.mf.plugin.pssd.ni;

import java.util.Collection;

import arc.mf.plugin.PluginService;
import arc.mf.plugin.dtype.EnumType;
import arc.mf.plugin.dtype.IntegerType;
import arc.mf.plugin.dtype.StringType;
import arc.mf.plugin.dtype.BooleanType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;
import arc.xml.XmlDoc.Element;

public class SvcUserCreate extends PluginService {

	private Interface _defn;

	public SvcUserCreate()  {
		_defn = new Interface();
		Interface.Element ie = new Interface.Element("authority",StringType.DEFAULT,"The authority of interest for users. Defaults to local.",0,1);
		ie.add(new Interface.Attribute("protocol", StringType.DEFAULT,
				"The protocol of the identity authority. If unspecified, defaults to federated user within the same type of repository.", 0));
		_defn.add(ie);
		_defn.add(new Interface.Element("domain", StringType.DEFAULT, "The name of the domain that the users will be created in. Defaults to 'nig'.", 0, 1));
		_defn.add(new Interface.Element("user", StringType.DEFAULT, "The username.", 1, 1));
		//
		Interface.Element me = new Interface.Element("name", StringType.DEFAULT, "User's name", 0, Integer.MAX_VALUE);
		me.add(new Interface.Attribute("type", new EnumType(new String[] {"first", "middle", "last"}),
				"Type of name", 1));
		_defn.add(me);
		//
		Interface.Element me2 = new Interface.Element("password", StringType.DEFAULT, "The user's password.", 0, 1);
		me2.add(new Interface.Attribute("notify", BooleanType.DEFAULT,
				"If notification has been requested for account creation, and this attribute it true, then the password will be included in the notification. Defaults to false.", 0));
		_defn.add(me2);
		//
		Interface.Element me3  = new Interface.Element("generate-password", BooleanType.DEFAULT, "Auto-generate the password and send to the user via the given email. Defaults to false.", 0, 1);
		me3.add(new Interface.Attribute("length", IntegerType.DEFAULT,
				"The password length. A length less than the minimum length for the domain will be ignored. If not specified, defaults to the minimum length for the domain.", 0));
		_defn.add(me3);
		//
		_defn.add(new Interface.Element("email", StringType.DEFAULT, "The user's email address", 1, 1));
		_defn.add(new Interface.Element("project-creator", BooleanType.DEFAULT, "Should this user be allowed to create projects ? Defaults to false.", 0, 1));
		_defn.add(new Interface.Element("notify", BooleanType.DEFAULT, "If true, and the user has an e-mail address then notify them of account creation. If generate-password is true, then e-mail the generated password to the user. Drfaults to false.", 0, 1));
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public Interface definition() {
		return _defn;
	}

	public String description() {
		return "Creates a standard Neuroimaging group (NIG) user and assigns their basic PSSD and NIG-PSSD roles.";
	}

	public String name() {
		return "nig.pssd.user.create";
	}

	public void execute(Element args, Inputs inputs, Outputs outputs, XmlWriter w) throws Throwable {
		// Must be system-admin 
		nig.mf.pssd.plugin.util.PSSDUtil.isSystemAdministrator(executor());

		
		// Inputs
		XmlDoc.Element authority = args.element("authority");
		String domain = args.stringValue("domain", "nig");
		Collection<XmlDoc.Element> names = args.elements("name");
		Boolean projectCreator = args.booleanValue("project-creator", false);
		XmlDoc.Element password = args.element("password");
		XmlDoc.Element notify = args.element("notify");
		XmlDoc.Element generate = args.element("generate-password");

		// Create user
		XmlDocMaker dm = new XmlDocMaker("args");
		if (authority!=null) dm.add("authority", authority);
		dm.add("domain", domain);
		dm.add("user", args.value("user"));
		if (password!=null) dm.add(password);
		if (notify!=null) dm.add(notify);
		if (generate!=null) dm.add(generate);
		dm.add("email", args.value("email"));
		dm.add("project-creator", projectCreator);
		if (names!=null) {
			for (XmlDoc.Element name : names) {
				dm.add(name);
			}
		}
		dm.add("notification", "The URL of the UM-NIG DaRIS system is https://daris-1.melbourne.nectar.org.au:8443/daris/DaRIS.html");
		executor().execute("om.pssd.user.create", dm.root());

		// Grant NIG role
		dm = new XmlDocMaker("args");
		if (authority!=null) dm.add(authority);
		dm.add("domain", domain);
		dm.add("user",args.value("user"));
		dm.add("role", "nig.pssd.model.user");
		executor().execute("om.pssd.user.role.grant", dm.root());
	}
}
