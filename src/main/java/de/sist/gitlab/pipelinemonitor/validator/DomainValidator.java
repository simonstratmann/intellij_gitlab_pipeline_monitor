//Taken from Apache Commons
package de.sist.gitlab.pipelinemonitor.validator;


import java.io.Serializable;
import java.net.IDN;
import java.util.Arrays;
import java.util.Locale;

/**
 * <p><b>Domain name</b> validation routines.</p>
 *
 * <p>
 * This validator provides methods for validating Internet domain names
 * and top-level domains.
 * </p>
 *
 * <p>Domain names are evaluated according
 * to the standards <a href="http://www.ietf.org/rfc/rfc1034.txt">RFC1034</a>,
 * section 3, and <a href="http://www.ietf.org/rfc/rfc1123.txt">RFC1123</a>,
 * section 2.1. No accommodation is provided for the specialized needs of
 * other applications; if the domain name has been URL-encoded, for example,
 * validation will fail even though the equivalent plaintext version of the
 * same name would have passed.
 * </p>
 *
 * <p>
 * Validation is also provided for top-level domains (TLDs) as defined and
 * maintained by the Internet Assigned Numbers Authority (IANA):
 * </p>
 *
 *   <ul>
 *     <li>{@link #isValidInfrastructureTld} - validates infrastructure TLDs
 *         (<code>.arpa</code>, etc.)</li>
 *     <li>{@link #isValidGenericTld} - validates generic TLDs
 *         (<code>.com, .org</code>, etc.)</li>
 *     <li>{@link #isValidCountryCodeTld} - validates country code TLDs
 *         (<code>.us, .uk, .cn</code>, etc.)</li>
 *   </ul>
 *
 * <p>
 * (<b>NOTE</b>: This class does not provide IP address lookup for domain names or
 * methods to ensure that a given domain name matches a specific IP; see
 * {@link java.net.InetAddress} for that functionality.)
 * </p>
 *
 * @version $Revision: 1781829 $
 * @since Validator 1.4
 */
public class DomainValidator implements Serializable {

    private static final int MAX_DOMAIN_LENGTH = 253;

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final long serialVersionUID = -4407125112880174009L;

    // Regular expression strings for hostnames (derived from RFC2396 and RFC 1123)

    // RFC2396: domainlabel   = alphanum | alphanum *( alphanum | "-" ) alphanum
    // Max 63 characters
    private static final String DOMAIN_LABEL_REGEX = "\\p{Alnum}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?";

    // RFC2396 toplabel = alpha | alpha *( alphanum | "-" ) alphanum
    // Max 63 characters
    private static final String TOP_LABEL_REGEX = "\\p{Alpha}(?>[\\p{Alnum}-]{0,61}\\p{Alnum})?";

    // RFC2396 hostname = *( domainlabel "." ) toplabel [ "." ]
    // Note that the regex currently requires both a domain label and a top level label, whereas
    // the RFC does not. This is because the regex is used to detect if a TLD is present.
    // If the match fails, input is checked against DOMAIN_LABEL_REGEX (hostnameRegex)
    // RFC1123 sec 2.1 allows hostnames to start with a digit
    private static final String DOMAIN_NAME_REGEX =
            "^(?:" + DOMAIN_LABEL_REGEX + "\\.)+" + "(" + TOP_LABEL_REGEX + ")\\.?$";

    private final boolean allowLocal;

    /**
     * Singleton instance of this validator, which
     * doesn't consider local addresses as valid.
     */
    private static final DomainValidator DOMAIN_VALIDATOR = new DomainValidator(false);

    /**
     * Singleton instance of this validator, which does
     * consider local addresses valid.
     */
    private static final DomainValidator DOMAIN_VALIDATOR_WITH_LOCAL = new DomainValidator(true);

    /**
     * RegexValidator for matching domains.
     */
    private final RegexValidator domainRegex =
            new RegexValidator(DOMAIN_NAME_REGEX);
    /**
     * RegexValidator for matching a local hostname
     */
    // RFC1123 sec 2.1 allows hostnames to start with a digit
    private final RegexValidator hostnameRegex =
            new RegexValidator(DOMAIN_LABEL_REGEX);

    /**
     * Returns the singleton instance of this validator. It
     * will not consider local addresses as valid.
     *
     * @return the singleton instance of this validator
     */
    public static synchronized DomainValidator getInstance() {
        inUse = true;
        return DOMAIN_VALIDATOR;
    }

    /**
     * Returns the singleton instance of this validator,
     * with local validation as required.
     *
     * @param allowLocal Should local addresses be considered valid?
     * @return the singleton instance of this validator
     */
    public static synchronized DomainValidator getInstance(boolean allowLocal) {
        inUse = true;
        if (allowLocal) {
            return DOMAIN_VALIDATOR_WITH_LOCAL;
        }
        return DOMAIN_VALIDATOR;
    }

    /**
     * Private constructor.
     */
    private DomainValidator(boolean allowLocal) {
        this.allowLocal = allowLocal;
    }

    /**
     * Returns true if the specified <code>String</code> parses
     * as a valid domain name with a recognized top-level domain.
     * The parsing is case-insensitive.
     *
     * @param domain the parameter to check for domain name syntax
     * @return true if the parameter is a valid domain name
     */
    public boolean isValid(String domain) {
        if (domain == null) {
            return false;
        }
        domain = unicodeToASCII(domain);
        // hosts must be equally reachable via punycode and Unicode;
        // Unicode is never shorter than punycode, so check punycode
        // if domain did not convert, then it will be caught by ASCII
        // checks in the regexes below
        if (domain.length() > MAX_DOMAIN_LENGTH) {
            return false;
        }
        String[] groups = domainRegex.match(domain);
        if (groups != null && groups.length > 0) {
            return isValidTld(groups[0]);
        }
        return allowLocal && hostnameRegex.isValid(domain);
    }

    // package protected for unit test access
    // must agree with isValid() above
    final boolean isValidDomainSyntax(String domain) {
        if (domain == null) {
            return false;
        }
        domain = unicodeToASCII(domain);
        // hosts must be equally reachable via punycode and Unicode;
        // Unicode is never shorter than punycode, so check punycode
        // if domain did not convert, then it will be caught by ASCII
        // checks in the regexes below
        if (domain.length() > MAX_DOMAIN_LENGTH) {
            return false;
        }
        String[] groups = domainRegex.match(domain);
        return (groups != null && groups.length > 0)
                || hostnameRegex.isValid(domain);
    }

    /**
     * Returns true if the specified <code>String</code> matches any
     * IANA-defined top-level domain. Leading dots are ignored if present.
     * The search is case-insensitive.
     *
     * @param tld the parameter to check for TLD status, not null
     * @return true if the parameter is a TLD
     */
    public boolean isValidTld(String tld) {
        tld = unicodeToASCII(tld);
        if (allowLocal && isValidLocalTld(tld)) {
            return true;
        }
        return isValidInfrastructureTld(tld)
                || isValidGenericTld(tld)
                || isValidCountryCodeTld(tld);
    }

    /**
     * Returns true if the specified <code>String</code> matches any
     * IANA-defined infrastructure top-level domain. Leading dots are
     * ignored if present. The search is case-insensitive.
     *
     * @param iTld the parameter to check for infrastructure TLD status, not null
     * @return true if the parameter is an infrastructure TLD
     */
    public boolean isValidInfrastructureTld(String iTld) {
        final String key = chompLeadingDot(unicodeToASCII(iTld).toLowerCase(Locale.ENGLISH));
        return arrayContains(INFRASTRUCTURE_TLDS, key);
    }

    /**
     * Returns true if the specified <code>String</code> matches any
     * IANA-defined generic top-level domain. Leading dots are ignored
     * if present. The search is case-insensitive.
     *
     * @param gTld the parameter to check for generic TLD status, not null
     * @return true if the parameter is a generic TLD
     */
    public boolean isValidGenericTld(String gTld) {
        final String key = chompLeadingDot(unicodeToASCII(gTld).toLowerCase(Locale.ENGLISH));
        return (arrayContains(GENERIC_TLDS, key) || arrayContains(genericTLDsPlus, key))
                && !arrayContains(genericTLDsMinus, key);
    }

    /**
     * Returns true if the specified <code>String</code> matches any
     * IANA-defined country code top-level domain. Leading dots are
     * ignored if present. The search is case-insensitive.
     *
     * @param ccTld the parameter to check for country code TLD status, not null
     * @return true if the parameter is a country code TLD
     */
    public boolean isValidCountryCodeTld(String ccTld) {
        final String key = chompLeadingDot(unicodeToASCII(ccTld).toLowerCase(Locale.ENGLISH));
        return (arrayContains(COUNTRY_CODE_TLDS, key) || arrayContains(countryCodeTLDsPlus, key))
                && !arrayContains(countryCodeTLDsMinus, key);
    }

    /**
     * Returns true if the specified <code>String</code> matches any
     * widely used "local" domains (localhost or localdomain). Leading dots are
     * ignored if present. The search is case-insensitive.
     *
     * @param lTld the parameter to check for local TLD status, not null
     * @return true if the parameter is an local TLD
     */
    public boolean isValidLocalTld(String lTld) {
        final String key = chompLeadingDot(unicodeToASCII(lTld).toLowerCase(Locale.ENGLISH));
        return arrayContains(LOCAL_TLDS, key);
    }

    private String chompLeadingDot(String str) {
        if (str.startsWith(".")) {
            return str.substring(1);
        }
        return str;
    }

    // ---------------------------------------------
    // ----- TLDs defined by IANA
    // ----- Authoritative and comprehensive list at:
    // ----- http://data.iana.org/TLD/tlds-alpha-by-domain.txt

    // Note that the above list is in UPPER case.
    // The code currently converts strings to lower case (as per the tables below)

    // IANA also provide an HTML list at http://www.iana.org/domains/root/db
    // Note that this contains several country code entries which are NOT in
    // the text file. These all have the "Not assigned" in the "Sponsoring Organisation" column
    // For example (as of 2015-01-02):
    // .bl  country-code    Not assigned
    // .um  country-code    Not assigned

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static final String[] INFRASTRUCTURE_TLDS = new String[]{
            "arpa",               // internet infrastructure
    };

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static final String[] GENERIC_TLDS = new String[]{
            // Taken from Version 2017020400, Last Updated Sat Feb  4 07:07:01 2017 UTC
            "aaa",
            "aarp",
            "abarth",
            "abb",
            "abbott",
            "abbvie",
            "abc",
            "able",
            "abogado",
            "abudhabi",
            "academy",
            "accenture",
            "accountant",
            "accountants",
            "aco",
            "active",
            "actor",
            "adac",
            "ads",
            "adult",
            "aeg",
            "aero",
            "aetna",
            "afamilycompany",
            "afl",
            "agakhan",
            "agency",
            "aig",
            "aigo",
            "airbus",
            "airforce",
            "airtel",
            "akdn",
            "alfaromeo",
            "alibaba",
            "alipay",
            "allfinanz",
            "allstate",
            "ally",
            "alsace",
            "alstom",
            "americanexpress",
            "americanfamily",
            "amex",
            "amfam",
            "amica",
            "amsterdam",
            "analytics",
            "android",
            "anquan",
            "anz",
            "aol",
            "apartments",
            "app",
            "apple",
            "aquarelle",
            "aramco",
            "archi",
            "army",
            "art",
            "arte",
            "asda",
            "asia",
            "associates",
            "athleta",
            "attorney",
            "auction",
            "audi",
            "audible",
            "audio",
            "auspost",
            "author",
            "auto",
            "autos",
            "avianca",
            "aws",
            "axa",
            "azure",
            "baby",
            "baidu",
            "banamex",
            "bananarepublic",
            "band",
            "bank",
            "bar",
            "barcelona",
            "barclaycard",
            "barclays",
            "barefoot",
            "bargains",
            "baseball",
            "basketball",
            "bauhaus",
            "bayern",
            "bbc",
            "bbt",
            "bbva",
            "bcg",
            "bcn",
            "beats",
            "beauty",
            "beer",
            "bentley",
            "berlin",
            "best",
            "bestbuy",
            "bet",
            "bharti",
            "bible",
            "bid",
            "bike",
            "bing",
            "bingo",
            "bio",
            "biz",
            "black",
            "blackfriday",
            "blanco",
            "blockbuster",
            "blog",
            "bloomberg",
            "blue",
            "bms",
            "bmw",
            "bnl",
            "bnpparibas",
            "boats",
            "boehringer",
            "bofa",
            "bom",
            "bond",
            "boo",
            "book",
            "booking",
            "boots",
            "bosch",
            "bostik",
            "boston",
            "bot",
            "boutique",
            "box",
            "bradesco",
            "bridgestone",
            "broadway",
            "broker",
            "brother",
            "brussels",
            "budapest",
            "bugatti",
            "build",
            "builders",
            "business",
            "buy",
            "buzz",
            "bzh",
            "cab",
            "cafe",
            "cal",
            "call",
            "calvinklein",
            "cam",
            "camera",
            "camp",
            "cancerresearch",
            "canon",
            "capetown",
            "capital",
            "capitalone",
            "car",
            "caravan",
            "cards",
            "care",
            "career",
            "careers",
            "cars",
            "cartier",
            "casa",
            "case",
            "caseih",
            "cash",
            "casino",
            "cat",
            "catering",
            "catholic",
            "cba",
            "cbn",
            "cbre",
            "cbs",
            "ceb",
            "center",
            "ceo",
            "cern",
            "cfa",
            "cfd",
            "chanel",
            "channel",
            "chase",
            "chat",
            "cheap",
            "chintai",
            "chloe",
            "christmas",
            "chrome",
            "chrysler",
            "church",
            "cipriani",
            "circle",
            "cisco",
            "citadel",
            "citi",
            "citic",
            "city",
            "cityeats",
            "claims",
            "cleaning",
            "click",
            "clinic",
            "clinique",
            "clothing",
            "cloud",
            "club",
            "clubmed",
            "coach",
            "codes",
            "coffee",
            "college",
            "cologne",
            "com",
            "comcast",
            "commbank",
            "community",
            "company",
            "compare",
            "computer",
            "comsec",
            "condos",
            "construction",
            "consulting",
            "contact",
            "contractors",
            "cooking",
            "cookingchannel",
            "cool",
            "coop",
            "corsica",
            "country",
            "coupon",
            "coupons",
            "courses",
            "credit",
            "creditcard",
            "creditunion",
            "cricket",
            "crown",
            "crs",
            "cruise",
            "cruises",
            "csc",
            "cuisinella",
            "cymru",
            "cyou",
            "dabur",
            "dad",
            "dance",
            "data",
            "date",
            "dating",
            "datsun",
            "day",
            "dclk",
            "dds",
            "deal",
            "dealer",
            "deals",
            "degree",
            "delivery",
            "dell",
            "deloitte",
            "delta",
            "democrat",
            "dental",
            "dentist",
            "desi",
            "design",
            "dev",
            "dhl",
            "diamonds",
            "diet",
            "digital",
            "direct",
            "directory",
            "discount",
            "discover",
            "dish",
            "diy",
            "dnp",
            "docs",
            "doctor",
            "dodge",
            "dog",
            "doha",
            "domains",
//            "doosan",
            "dot",
            "download",
            "drive",
            "dtv",
            "dubai",
            "duck",
            "dunlop",
            "duns",
            "dupont",
            "durban",
            "dvag",
            "dvr",
            "earth",
            "eat",
            "eco",
            "edeka",
            "edu",
            "education",
            "email",
            "emerck",
            "energy",
            "engineer",
            "engineering",
            "enterprises",
            "epost",
            "epson",
            "equipment",
            "ericsson",
            "erni",
            "esq",
            "estate",
            "esurance",
            "eurovision",
            "eus",
            "events",
            "everbank",
            "exchange",
            "expert",
            "exposed",
            "express",
            "extraspace",
            "fage",
            "fail",
            "fairwinds",
            "faith",
            "family",
            "fan",
            "fans",
            "farm",
            "farmers",
            "fashion",
            "fast",
            "fedex",
            "feedback",
            "ferrari",
            "ferrero",
            "fiat",
            "fidelity",
            "fido",
            "film",
            "final",
            "finance",
            "financial",
            "fire",
            "firestone",
            "firmdale",
            "fish",
            "fishing",
            "fit",
            "fitness",
            "flickr",
            "flights",
            "flir",
            "florist",
            "flowers",
//        "flsmidth",
            "fly",
            "foo",
            "food",
            "foodnetwork",
            "football",
            "ford",
            "forex",
            "forsale",
            "forum",
            "foundation",
            "fox",
            "free",
            "fresenius",
            "frl",
            "frogans",
            "frontdoor",
            "frontier",
            "ftr",
            "fujitsu",
            "fujixerox",
            "fun",
            "fund",
            "furniture",
            "futbol",
            "fyi",
            "gal",
            "gallery",
            "gallo",
            "gallup",
            "game",
            "games",
            "gap",
            "garden",
            "gbiz",
            "gdn",
            "gea",
            "gent",
            "genting",
            "george",
            "ggee",
            "gift",
            "gifts",
            "gives",
            "giving",
            "glade",
            "glass",
            "gle",
            "global",
            "globo",
            "gmail",
            "gmbh",
            "gmo",
            "gmx",
            "godaddy",
            "gold",
            "goldpoint",
            "golf",
            "goo",
            "goodhands",
            "goodyear",
            "goog",
            "google",
            "gop",
            "got",
            "gov",
            "grainger",
            "graphics",
            "gratis",
            "green",
            "gripe",
            "group",
            "guardian",
            "gucci",
            "guge",
            "guide",
            "guitars",
            "guru",
            "hair",
            "hamburg",
            "hangout",
            "haus",
            "hbo",
            "hdfc",
            "hdfcbank",
            "health",
            "healthcare",
            "help",
            "helsinki",
            "here",
            "hermes",
            "hgtv",
            "hiphop",
            "hisamitsu",
            "hitachi",
            "hiv",
            "hkt",
            "hockey",
            "holdings",
            "holiday",
            "homedepot",
            "homegoods",
            "homes",
            "homesense",
            "honda",
            "honeywell",
            "horse",
            "hospital",
            "host",
            "hosting",
            "hot",
            "hoteles",
            "hotmail",
            "house",
            "how",
            "hsbc",
            "htc",
            "hughes",
            "hyatt",
            "hyundai",
            "ibm",
            "icbc",
            "ice",
            "icu",
            "ieee",
            "ifm",
//        "iinet",
            "ikano",
            "imamat",
            "imdb",
            "immo",
            "immobilien",
            "industries",
            "infiniti",
            "info",
            "ing",
            "ink",
            "institute",
            "insurance",
            "insure",
            "int",
            "intel",
            "international",
            "intuit",
            "investments",
            "ipiranga",
            "irish",
            "iselect",
            "ismaili",
            "ist",
            "istanbul",
            "itau",
            "itv",
            "iveco",
            "iwc",
            "jaguar",
            "java",
            "jcb",
            "jcp",
            "jeep",
            "jetzt",
            "jewelry",
            "jio",
            "jlc",
            "jll",
            "jmp",
            "jnj",
            "jobs",
            "joburg",
            "jot",
            "joy",
            "jpmorgan",
            "jprs",
            "juegos",
            "juniper",
            "kaufen",
            "kddi",
            "kerryhotels",
            "kerrylogistics",
            "kerryproperties",
            "kfh",
            "kia",
            "kim",
            "kinder",
            "kindle",
            "kitchen",
            "kiwi",
            "koeln",
            "komatsu",
            "kosher",
            "kpmg",
            "kpn",
            "krd",
            "kred",
            "kuokgroup",
            "kyoto",
            "lacaixa",
            "ladbrokes",
            "lamborghini",
            "lamer",
            "lancaster",
            "lancia",
            "lancome",
            "land",
            "landrover",
            "lanxess",
            "lasalle",
            "lat",
            "latino",
            "latrobe",
            "law",
            "lawyer",
            "lds",
            "lease",
            "leclerc",
            "lefrak",
            "legal",
            "lego",
            "lexus",
            "lgbt",
            "liaison",
            "lidl",
            "life",
            "lifeinsurance",
            "lifestyle",
            "lighting",
            "like",
            "lilly",
            "limited",
            "limo",
            "lincoln",
            "linde",
            "link",
            "lipsy",
            "live",
            "living",
            "lixil",
            "loan",
            "loans",
            "locker",
            "locus",
            "loft",
            "lol",
            "london",
            "lotte",
            "lotto",
            "love",
            "lpl",
            "lplfinancial",
            "ltd",
            "ltda",
            "lundbeck",
            "lupin",
            "luxe",
            "luxury",
            "macys",
            "madrid",
            "maif",
            "maison",
            "makeup",
            "man",
            "management",
            "mango",
            "market",
            "marketing",
            "markets",
            "marriott",
            "marshalls",
            "maserati",
            "mattel",
            "mba",
            "mcd",
            "mcdonalds",
            "mckinsey",
            "med",
            "media",
            "meet",
            "melbourne",
            "meme",
            "memorial",
            "men",
            "menu",
            "meo",
            "metlife",
            "miami",
            "microsoft",
            "mil",
            "mini",
            "mint",
            "mit",
            "mitsubishi",
            "mlb",
            "mls",
            "mma",
            "mobi",
            "mobile",
            "mobily",
            "moda",
            "moe",
            "moi",
            "mom",
            "monash",
            "money",
            "monster",
            "montblanc",
            "mopar",
            "mormon",
            "mortgage",
            "moscow",
            "moto",
            "motorcycles",
            "mov",
            "movie",
            "movistar",
            "msd",
            "mtn",
            "mtpc",
            "mtr",
            "museum",
            "mutual",
//        "mutuelle",
            "nab",
            "nadex",
            "nagoya",
            "name",
            "nationwide",
            "natura",
            "navy",
            "nba",
            "nec",
            "net",
            "netbank",
            "netflix",
            "network",
            "neustar",
            "new",
            "newholland",
            "news",
            "next",
            "nextdirect",
            "nexus",
            "nfl",
            "ngo",
            "nhk",
            "nico",
            "nike",
            "nikon",
            "ninja",
            "nissan",
            "nissay",
            "nokia",
            "northwesternmutual",
            "norton",
            "now",
            "nowruz",
            "nowtv",
            "nra",
            "nrw",
            "ntt",
            "nyc",
            "obi",
            "observer",
            "off",
            "office",
            "okinawa",
            "olayan",
            "olayangroup",
            "oldnavy",
            "ollo",
            "omega",
            "one",
            "ong",
            "onl",
            "online",
            "onyourside",
            "ooo",
            "open",
            "oracle",
            "orange",
            "org",
            "organic",
            "orientexpress",
            "origins",
            "osaka",
            "otsuka",
            "ott",
            "ovh",
            "page",
            "pamperedchef",
            "panasonic",
            "panerai",
            "paris",
            "pars",
            "partners",
            "parts",
            "party",
            "passagens",
            "pay",
            "pccw",
            "pet",
            "pfizer",
            "pharmacy",
            "philips",
            "phone",
            "photo",
            "photography",
            "photos",
            "physio",
            "piaget",
            "pics",
            "pictet",
            "pictures",
            "pid",
            "pin",
            "ping",
            "pink",
            "pioneer",
            "pizza",
            "place",
            "play",
            "playstation",
            "plumbing",
            "plus",
            "pnc",
            "pohl",
            "poker",
            "politie",
            "porn",
            "post",
            "pramerica",
            "praxi",
            "press",
            "prime",
            "pro",
            "prod",
            "productions",
            "prof",
            "progressive",
            "promo",
            "properties",
            "property",
            "protection",
            "pru",
            "prudential",
            "pub",
            "pwc",
            "qpon",
            "quebec",
            "quest",
            "qvc",
            "racing",
            "radio",
            "raid",
            "read",
            "realestate",
            "realtor",
            "realty",
            "recipes",
            "red",
            "redstone",
            "redumbrella",
            "rehab",
            "reise",
            "reisen",
            "reit",
            "reliance",
            "ren",
            "rent",
            "rentals",
            "repair",
            "report",
            "republican",
            "rest",
            "restaurant",
            "review",
            "reviews",
            "rexroth",
            "rich",
            "richardli",
            "ricoh",
            "rightathome",
            "ril",
            "rio",
            "rip",
            "rmit",
            "rocher",
            "rocks",
            "rodeo",
            "rogers",
            "room",
            "rsvp",
            "ruhr",
            "run",
            "rwe",
            "ryukyu",
            "saarland",
            "safe",
            "safety",
            "sakura",
            "sale",
            "salon",
            "samsclub",
            "samsung",
            "sandvik",
            "sandvikcoromant",
            "sanofi",
            "sap",
            "sapo",
            "sarl",
            "sas",
            "save",
            "saxo",
            "sbi",
            "sbs",
            "sca",
            "scb",
            "schaeffler",
            "schmidt",
            "scholarships",
            "school",
            "schule",
            "schwarz",
            "science",
            "scjohnson",
            "scor",
            "scot",
            "seat",
            "secure",
            "security",
            "seek",
            "select",
            "sener",
            "services",
            "ses",
            "seven",
            "sew",
            "sex",
            "sexy",
            "sfr",
            "shangrila",
            "sharp",
            "shaw",
            "shell",
            "shia",
            "shiksha",
            "shoes",
            "shop",
            "shopping",
            "shouji",
            "show",
            "showtime",
            "shriram",
            "silk",
            "sina",
            "singles",
            "site",
            "ski",
            "skin",
            "sky",
            "skype",
            "sling",
            "smart",
            "smile",
            "sncf",
            "soccer",
            "social",
            "softbank",
            "software",
            "sohu",
            "solar",
            "solutions",
            "song",
            "sony",
            "soy",
            "space",
            "spiegel",
            "spot",
            "spreadbetting",
            "srl",
            "srt",
            "stada",
            "staples",
            "star",
            "starhub",
            "statebank",
            "statefarm",
            "statoil",
            "stc",
            "stcgroup",
            "stockholm",
            "storage",
            "store",
            "stream",
            "studio",
            "study",
            "style",
            "sucks",
            "supplies",
            "supply",
            "support",
            "surf",
            "surgery",
            "suzuki",
            "swatch",
            "swiftcover",
            "swiss",
            "sydney",
            "symantec",
            "systems",
            "tab",
            "taipei",
            "talk",
            "taobao",
            "target",
            "tatamotors",
            "tatar",
            "tattoo",
            "tax",
            "taxi",
            "tci",
            "tdk",
            "team",
            "tech",
            "technology",
            "tel",
            "telecity",
            "telefonica",
            "temasek",
            "tennis",
            "teva",
            "thd",
            "theater",
            "theatre",
            "tiaa",
            "tickets",
            "tienda",
            "tiffany",
            "tips",
            "tires",
            "tirol",
            "tjmaxx",
            "tjx",
            "tkmaxx",
            "tmall",
            "today",
            "tokyo",
            "tools",
            "top",
            "toray",
            "toshiba",
            "total",
            "tours",
            "town",
            "toyota",
            "toys",
            "trade",
            "trading",
            "training",
            "travel",
            "travelchannel",
            "travelers",
            "travelersinsurance",
            "trust",
            "trv",
            "tube",
            "tui",
            "tunes",
            "tushu",
            "tvs",
            "ubank",
            "ubs",
            "uconnect",
            "unicom",
            "university",
            "uno",
            "uol",
            "ups",
            "vacations",
            "vana",
            "vanguard",
            "vegas",
            "ventures",
            "verisign",
            "versicherung",
            "vet",
            "viajes",
            "video",
            "vig",
            "viking",
            "villas",
            "vin",
            "vip",
            "virgin",
            "visa",
            "vision",
            "vista",
            "vistaprint",
            "viva",
            "vivo",
            "vlaanderen",
            "vodka",
            "volkswagen",
            "volvo",
            "vote",
            "voting",
            "voto",
            "voyage",
            "vuelos",
            "wales",
            "walmart",
            "walter",
            "wang",
            "wanggou",
            "warman",
            "watch",
            "watches",
            "weather",
            "weatherchannel",
            "webcam",
            "weber",
            "website",
            "wed",
            "wedding",
            "weibo",
            "weir",
            "whoswho",
            "wien",
            "wiki",
            "williamhill",
            "win",
            "windows",
            "wine",
            "winners",
            "wme",
            "wolterskluwer",
            "woodside",
            "work",
            "works",
            "world",
            "wow",
            "wtc",
            "wtf",
            "xbox",
            "xerox",
            "xfinity",
            "xihuan",
            "xin",
            "xn--11b4c3d",
            "xn--1ck2e1b",
            "xn--1qqw23a",
            "xn--30rr7y",
            "xn--3bst00m",
            "xn--3ds443g",
            "xn--3oq18vl8pn36a",
            "xn--3pxu8k",
            "xn--42c2d9a",
            "xn--45q11c",
            "xn--4gbrim",
            "xn--55qw42g",
            "xn--55qx5d",
            "xn--5su34j936bgsg",
            "xn--5tzm5g",
            "xn--6frz82g",
            "xn--6qq986b3xl",
            "xn--80adxhks",
            "xn--80aqecdr1a",
            "xn--80asehdb",
            "xn--80aswg",
            "xn--8y0a063a",
            "xn--90ae",
            "xn--9dbq2a",
            "xn--9et52u",
            "xn--9krt00a",
            "xn--b4w605ferd",
            "xn--bck1b9a5dre4c",
            "xn--c1avg",
            "xn--c2br7g",
            "xn--cck2b3b",
            "xn--cg4bki",
            "xn--czr694b",
            "xn--czrs0t",
            "xn--czru2d",
            "xn--d1acj3b",
            "xn--eckvdtc9d",
            "xn--efvy88h",
            "xn--estv75g",
            "xn--fct429k",
            "xn--fhbei",
            "xn--fiq228c5hs",
            "xn--fiq64b",
            "xn--fjq720a",
            "xn--flw351e",
            "xn--fzys8d69uvgm",
            "xn--g2xx48c",
            "xn--gckr3f0f",
            "xn--gk3at1e",
            "xn--hxt814e",
            "xn--i1b6b1a6a2e",
            "xn--imr513n",
            "xn--io0a7i",
            "xn--j1aef",
            "xn--jlq61u9w7b",
            "xn--jvr189m",
            "xn--kcrx77d1x4a",
            "xn--kpu716f",
            "xn--kput3i",
            "xn--mgba3a3ejt",
            "xn--mgba7c0bbn0a",
            "xn--mgbab2bd",
            "xn--mgbb9fbpob",
            "xn--mgbca7dzdo",
            "xn--mgbi4ecexp",
            "xn--mgbt3dhd",
            "xn--mk1bu44c",
            "xn--mxtq1m",
            "xn--ngbc5azd",
            "xn--ngbe9e0a",
            "xn--nqv7f",
            "xn--nqv7fs00ema",
            "xn--nyqy26a",
            "xn--p1acf",
            "xn--pbt977c",
            "xn--pssy2u",
            "xn--q9jyb4c",
            "xn--qcka1pmc",
            "xn--rhqv96g",
            "xn--rovu88b",
            "xn--ses554g",
            "xn--t60b56a",
            "xn--tckwe",
            "xn--tiq49xqyj",
            "xn--unup4y",
            "xn--vermgensberater-ctb",
            "xn--vermgensberatung-pwb",
            "xn--vhquv",
            "xn--vuq861b",
            "xn--w4r85el8fhu5dnra",
            "xn--w4rs40l",
            "xn--xhq521b",
            "xn--zfr164b",
            "xperia",
            "xxx",
            "xyz",
            "yachts",
            "yahoo",
            "yamaxun",
            "yandex",
            "yodobashi",
            "yoga",
            "yokohama",
            "you",
            "youtube",
            "yun",
            "zappos",
            "zara",
            "zero",
            "zip",
            "zippo",
            "zone",
            "zuerich",
    };

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static final String[] COUNTRY_CODE_TLDS = new String[]{
            "ac",                 // Ascension Island
            "ad",                 // Andorra
            "ae",                 // United Arab Emirates
            "af",                 // Afghanistan
            "ag",                 // Antigua and Barbuda
            "ai",                 // Anguilla
            "al",                 // Albania
            "am",                 // Armenia
//        "an",                 // Netherlands Antilles (retired)
            "ao",                 // Angola
            "aq",                 // Antarctica
            "ar",                 // Argentina
            "as",                 // American Samoa
            "at",                 // Austria
            "au",                 // Australia (includes Ashmore and Cartier Islands and Coral Sea Islands)
            "aw",                 // Aruba
            "ax",                 // Åland
            "az",                 // Azerbaijan
            "ba",                 // Bosnia and Herzegovina
            "bb",                 // Barbados
            "bd",                 // Bangladesh
            "be",                 // Belgium
            "bf",                 // Burkina Faso
            "bg",                 // Bulgaria
            "bh",                 // Bahrain
            "bi",                 // Burundi
            "bj",                 // Benin
            "bm",                 // Bermuda
            "bn",                 // Brunei Darussalam
            "bo",                 // Bolivia
            "br",                 // Brazil
            "bs",                 // Bahamas
            "bt",                 // Bhutan
            "bv",                 // Bouvet Island
            "bw",                 // Botswana
            "by",                 // Belarus
            "bz",                 // Belize
            "ca",                 // Canada
            "cc",                 // Cocos (Keeling) Islands
            "cd",                 // Democratic Republic of the Congo (formerly Zaire)
            "cf",                 // Central African Republic
            "cg",                 // Republic of the Congo
            "ch",                 // Switzerland
            "ci",                 // Côte d'Ivoire
            "ck",                 // Cook Islands
            "cl",                 // Chile
            "cm",                 // Cameroon
            "cn",                 // China, mainland
            "co",                 // Colombia
            "cr",                 // Costa Rica
            "cu",                 // Cuba
            "cv",                 // Cape Verde
            "cw",                 // Curaçao
            "cx",                 // Christmas Island
            "cy",                 // Cyprus
            "cz",                 // Czech Republic
            "de",                 // Germany
            "dj",                 // Djibouti
            "dk",                 // Denmark
            "dm",                 // Dominica
            "do",                 // Dominican Republic
            "dz",                 // Algeria
            "ec",                 // Ecuador
            "ee",                 // Estonia
            "eg",                 // Egypt
            "er",                 // Eritrea
            "es",                 // Spain
            "et",                 // Ethiopia
            "eu",                 // European Union
            "fi",                 // Finland
            "fj",                 // Fiji
            "fk",                 // Falkland Islands
            "fm",                 // Federated States of Micronesia
            "fo",                 // Faroe Islands
            "fr",                 // France
            "ga",                 // Gabon
            "gb",                 // Great Britain (United Kingdom)
            "gd",                 // Grenada
            "ge",                 // Georgia
            "gf",                 // French Guiana
            "gg",                 // Guernsey
            "gh",                 // Ghana
            "gi",                 // Gibraltar
            "gl",                 // Greenland
            "gm",                 // The Gambia
            "gn",                 // Guinea
            "gp",                 // Guadeloupe
            "gq",                 // Equatorial Guinea
            "gr",                 // Greece
            "gs",                 // South Georgia and the South Sandwich Islands
            "gt",                 // Guatemala
            "gu",                 // Guam
            "gw",                 // Guinea-Bissau
            "gy",                 // Guyana
            "hk",                 // Hong Kong
            "hm",                 // Heard Island and McDonald Islands
            "hn",                 // Honduras
            "hr",                 // Croatia (Hrvatska)
            "ht",                 // Haiti
            "hu",                 // Hungary
            "id",                 // Indonesia
            "ie",                 // Ireland (Éire)
            "il",                 // Israel
            "im",                 // Isle of Man
            "in",                 // India
            "io",                 // British Indian Ocean Territory
            "iq",                 // Iraq
            "ir",                 // Iran
            "is",                 // Iceland
            "it",                 // Italy
            "je",                 // Jersey
            "jm",                 // Jamaica
            "jo",                 // Jordan
            "jp",                 // Japan
            "ke",                 // Kenya
            "kg",                 // Kyrgyzstan
            "kh",                 // Cambodia (Khmer)
            "ki",                 // Kiribati
            "km",                 // Comoros
            "kn",                 // Saint Kitts and Nevis
            "kp",                 // North Korea
            "kr",                 // South Korea
            "kw",                 // Kuwait
            "ky",                 // Cayman Islands
            "kz",                 // Kazakhstan
            "la",                 // Laos (currently being marketed as the official domain for Los Angeles)
            "lb",                 // Lebanon
            "lc",                 // Saint Lucia
            "li",                 // Liechtenstein
            "lk",                 // Sri Lanka
            "lr",                 // Liberia
            "ls",                 // Lesotho
            "lt",                 // Lithuania
            "lu",                 // Luxembourg
            "lv",                 // Latvia
            "ly",                 // Libya
            "ma",                 // Morocco
            "mc",                 // Monaco
            "md",                 // Moldova
            "me",                 // Montenegro
            "mg",                 // Madagascar
            "mh",                 // Marshall Islands
            "mk",                 // Republic of Macedonia
            "ml",                 // Mali
            "mm",                 // Myanmar
            "mn",                 // Mongolia
            "mo",                 // Macau
            "mp",                 // Northern Mariana Islands
            "mq",                 // Martinique
            "mr",                 // Mauritania
            "ms",                 // Montserrat
            "mt",                 // Malta
            "mu",                 // Mauritius
            "mv",                 // Maldives
            "mw",                 // Malawi
            "mx",                 // Mexico
            "my",                 // Malaysia
            "mz",                 // Mozambique
            "na",                 // Namibia
            "nc",                 // New Caledonia
            "ne",                 // Niger
            "nf",                 // Norfolk Island
            "ng",                 // Nigeria
            "ni",                 // Nicaragua
            "nl",                 // Netherlands
            "no",                 // Norway
            "np",                 // Nepal
            "nr",                 // Nauru
            "nu",                 // Niue
            "nz",                 // New Zealand
            "om",                 // Oman
            "pa",                 // Panama
            "pe",                 // Peru
            "pf",                 // French Polynesia With Clipperton Island
            "pg",                 // Papua New Guinea
            "ph",                 // Philippines
            "pk",                 // Pakistan
            "pl",                 // Poland
            "pm",                 // Saint-Pierre and Miquelon
            "pn",                 // Pitcairn Islands
            "pr",                 // Puerto Rico
            "ps",                 // Palestinian territories (PA-controlled West Bank and Gaza Strip)
            "pt",                 // Portugal
            "pw",                 // Palau
            "py",                 // Paraguay
            "qa",                 // Qatar
            "re",                 // Réunion
            "ro",                 // Romania
            "rs",                 // Serbia
            "ru",                 // Russia
            "rw",                 // Rwanda
            "sa",                 // Saudi Arabia
            "sb",                 // Solomon Islands
            "sc",                 // Seychelles
            "sd",                 // Sudan
            "se",                 // Sweden
            "sg",                 // Singapore
            "sh",                 // Saint Helena
            "si",                 // Slovenia
            "sj",                 // Svalbard and Jan Mayen Islands Not in use (Norwegian dependencies; see .no)
            "sk",                 // Slovakia
            "sl",                 // Sierra Leone
            "sm",                 // San Marino
            "sn",                 // Senegal
            "so",                 // Somalia
            "sr",                 // Suriname
            "st",                 // São Tomé and Príncipe
            "su",                 // Soviet Union (deprecated)
            "sv",                 // El Salvador
            "sx",                 // Sint Maarten
            "sy",                 // Syria
            "sz",                 // Swaziland
            "tc",                 // Turks and Caicos Islands
            "td",                 // Chad
            "tf",                 // French Southern and Antarctic Lands
            "tg",                 // Togo
            "th",                 // Thailand
            "tj",                 // Tajikistan
            "tk",                 // Tokelau
            "tl",                 // East Timor (deprecated old code)
            "tm",                 // Turkmenistan
            "tn",                 // Tunisia
            "to",                 // Tonga
//        "tp",                 // East Timor (Retired)
            "tr",                 // Turkey
            "tt",                 // Trinidad and Tobago
            "tv",                 // Tuvalu
            "tw",                 // Taiwan, Republic of China
            "tz",                 // Tanzania
            "ua",                 // Ukraine
            "ug",                 // Uganda
            "uk",                 // United Kingdom
            "us",                 // United States of America
            "uy",                 // Uruguay
            "uz",                 // Uzbekistan
            "va",                 // Vatican City State
            "vc",                 // Saint Vincent and the Grenadines
            "ve",                 // Venezuela
            "vg",                 // British Virgin Islands
            "vi",                 // U.S. Virgin Islands
            "vn",                 // Vietnam
            "vu",                 // Vanuatu
            "wf",                 // Wallis and Futuna
            "ws",                 // Samoa (formerly Western Samoa)
            "xn--3e0b707e",
            "xn--45brj9c",
            "xn--54b7fta0cc",
            "xn--80ao21a",
            "xn--90a3ac",
            "xn--90ais",
            "xn--clchc0ea0b2g2a9gcd",
            "xn--d1alf",
            "xn--e1a4c",
            "xn--fiqs8s",
            "xn--fiqz9s",
            "xn--fpcrj9c3d",
            "xn--fzc2c9e2c",
            "xn--gecrj9c",
            "xn--h2brj9c",
            "xn--j1amh",
            "xn--j6w193g",
            "xn--kprw13d",
            "xn--kpry57d",
            "xn--l1acc",
            "xn--lgbbat1ad8j",
            "xn--mgb9awbf",
            "xn--mgba3a4f16a",
            "xn--mgbaam7a8h",
            "xn--mgbayh7gpa",
            "xn--mgbbh1a71e",
            "xn--mgbc0a9azcg",
            "xn--mgberp4a5d4ar",
            "xn--mgbpl2fh",
            "xn--mgbtx2b",
            "xn--mgbx4cd0ab",
            "xn--mix891f",
            "xn--node",
            "xn--o3cw4h",
            "xn--ogbpf8fl",
            "xn--p1ai",
            "xn--pgbs0dh",
            "xn--qxam",
            "xn--s9brj9c",
            "xn--wgbh1c",
            "xn--wgbl6a",
            "xn--xkc2al3hye2a",
            "xn--xkc2dl3a5ee0h",
            "xn--y9a3aq",
            "xn--yfro4i67o",
            "xn--ygbi2ammx",
            "ye",                 // Yemen
            "yt",                 // Mayotte
            "za",                 // South Africa
            "zm",                 // Zambia
            "zw",                 // Zimbabwe
    };

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static final String[] LOCAL_TLDS = new String[]{
            "localdomain",         // Also widely used as localhost.localdomain
            "localhost",           // RFC2606 defined
    };

    // Additional arrays to supplement or override the built in ones.
    // The PLUS arrays are valid keys, the MINUS arrays are invalid keys

    /*
     * This field is used to detect whether the getInstance has been called.
     * After this, the method updateTLDOverride is not allowed to be called.
     * This field does not need to be volatile since it is only accessed from
     * synchronized methods.
     */
    private static boolean inUse = false;

    /*
     * These arrays are mutable, but they don't need to be volatile.
     * They can only be updated by the updateTLDOverride method, and any readers must get an instance
     * using the getInstance methods which are all (now) synchronised.
     */
    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static volatile String[] countryCodeTLDsPlus = EMPTY_STRING_ARRAY;

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static volatile String[] genericTLDsPlus = EMPTY_STRING_ARRAY;

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static volatile String[] countryCodeTLDsMinus = EMPTY_STRING_ARRAY;

    // WARNING: this array MUST be sorted, otherwise it cannot be searched reliably using binary search
    private static volatile String[] genericTLDsMinus = EMPTY_STRING_ARRAY;

    /**
     * enum used by {@link DomainValidator#updateTLDOverride(ArrayType, String[])}
     * to determine which override array to update / fetch
     *
     * @since 1.5.0
     * @since 1.5.1 made public and added read-only array references
     */
    public enum ArrayType {
        /**
         * Update (or get a copy of) the GENERIC_TLDS_PLUS table containing additonal generic TLDs
         */
        GENERIC_PLUS,
        /**
         * Update (or get a copy of) the GENERIC_TLDS_MINUS table containing deleted generic TLDs
         */
        GENERIC_MINUS,
        /**
         * Update (or get a copy of) the COUNTRY_CODE_TLDS_PLUS table containing additonal country code TLDs
         */
        COUNTRY_CODE_PLUS,
        /**
         * Update (or get a copy of) the COUNTRY_CODE_TLDS_MINUS table containing deleted country code TLDs
         */
        COUNTRY_CODE_MINUS,
        /**
         * Get a copy of the generic TLDS table
         */
        GENERIC_RO,
        /**
         * Get a copy of the country code table
         */
        COUNTRY_CODE_RO,
        /**
         * Get a copy of the infrastructure table
         */
        INFRASTRUCTURE_RO,
        /**
         * Get a copy of the local table
         */
        LOCAL_RO;
    }

    ;

    // For use by unit test code only
    static synchronized void clearTLDOverrides() {
        inUse = false;
        countryCodeTLDsPlus = EMPTY_STRING_ARRAY;
        countryCodeTLDsMinus = EMPTY_STRING_ARRAY;
        genericTLDsPlus = EMPTY_STRING_ARRAY;
        genericTLDsMinus = EMPTY_STRING_ARRAY;
    }

    /**
     * Update one of the TLD override arrays.
     * This must only be done at program startup, before any instances are accessed using getInstance.
     * <p>
     * For example:
     * <p>
     * {@code DomainValidator.updateTLDOverride(ArrayType.GENERIC_PLUS, new String[]{"apache"})}
     * <p>
     * To clear an override array, provide an empty array.
     *
     * @param table the table to update, see {@link ArrayType}
     *              Must be one of the following
     *              <ul>
     *              <li>COUNTRY_CODE_MINUS</li>
     *              <li>COUNTRY_CODE_PLUS</li>
     *              <li>GENERIC_MINUS</li>
     *              <li>GENERIC_PLUS</li>
     *              </ul>
     * @param tlds  the array of TLDs, must not be null
     * @throws IllegalStateException    if the method is called after getInstance
     * @throws IllegalArgumentException if one of the read-only tables is requested
     * @since 1.5.0
     */
    public static synchronized void updateTLDOverride(ArrayType table, String[] tlds) {
        if (inUse) {
            throw new IllegalStateException("Can only invoke this method before calling getInstance");
        }
        String[] copy = new String[tlds.length];
        // Comparisons are always done with lower-case entries
        for (int i = 0; i < tlds.length; i++) {
            copy[i] = tlds[i].toLowerCase(Locale.ENGLISH);
        }
        Arrays.sort(copy);
        switch (table) {
            case COUNTRY_CODE_MINUS:
                countryCodeTLDsMinus = copy;
                break;
            case COUNTRY_CODE_PLUS:
                countryCodeTLDsPlus = copy;
                break;
            case GENERIC_MINUS:
                genericTLDsMinus = copy;
                break;
            case GENERIC_PLUS:
                genericTLDsPlus = copy;
                break;
            case COUNTRY_CODE_RO:
            case GENERIC_RO:
            case INFRASTRUCTURE_RO:
            case LOCAL_RO:
                throw new IllegalArgumentException("Cannot update the table: " + table);
            default:
                throw new IllegalArgumentException("Unexpected enum value: " + table);
        }
    }

    /**
     * Get a copy of the internal array.
     *
     * @param table the array type (any of the enum values)
     * @return a copy of the array
     * @throws IllegalArgumentException if the table type is unexpected (should not happen)
     * @since 1.5.1
     */
    public static String[] getTLDEntries(ArrayType table) {
        final String array[];
        switch (table) {
            case COUNTRY_CODE_MINUS:
                array = countryCodeTLDsMinus;
                break;
            case COUNTRY_CODE_PLUS:
                array = countryCodeTLDsPlus;
                break;
            case GENERIC_MINUS:
                array = genericTLDsMinus;
                break;
            case GENERIC_PLUS:
                array = genericTLDsPlus;
                break;
            case GENERIC_RO:
                array = GENERIC_TLDS;
                break;
            case COUNTRY_CODE_RO:
                array = COUNTRY_CODE_TLDS;
                break;
            case INFRASTRUCTURE_RO:
                array = INFRASTRUCTURE_TLDS;
                break;
            case LOCAL_RO:
                array = LOCAL_TLDS;
                break;
            default:
                throw new IllegalArgumentException("Unexpected enum value: " + table);
        }
        return Arrays.copyOf(array, array.length); // clone the array
    }

    /**
     * Converts potentially Unicode input to punycode.
     * If conversion fails, returns the original input.
     *
     * @param input the string to convert, not null
     * @return converted input, or original input if conversion fails
     */
    // Needed by UrlValidator
    static String unicodeToASCII(String input) {
        if (isOnlyASCII(input)) { // skip possibly expensive processing
            return input;
        }
        try {
            final String ascii = IDN.toASCII(input);
            if (IDNBUGHOLDER.IDN_TOASCII_PRESERVES_TRAILING_DOTS) {
                return ascii;
            }
            final int length = input.length();
            if (length == 0) {// check there is a last character
                return input;
            }
            // RFC3490 3.1. 1)
            //            Whenever dots are used as label separators, the following
            //            characters MUST be recognized as dots: U+002E (full stop), U+3002
            //            (ideographic full stop), U+FF0E (fullwidth full stop), U+FF61
            //            (halfwidth ideographic full stop).
            char lastChar = input.charAt(length - 1);// fetch original last char
            switch (lastChar) {
                case '\u002E': // "." full stop
                case '\u3002': // ideographic full stop
                case '\uFF0E': // fullwidth full stop
                case '\uFF61': // halfwidth ideographic full stop
                    return ascii + "."; // restore the missing stop
                default:
                    return ascii;
            }
        } catch (IllegalArgumentException e) { // input is not valid
            return input;
        }
    }

    private static class IDNBUGHOLDER {
        private static boolean keepsTrailingDot() {
            final String input = "a."; // must be a valid name
            return input.equals(IDN.toASCII(input));
        }

        private static final boolean IDN_TOASCII_PRESERVES_TRAILING_DOTS = keepsTrailingDot();
    }

    /*
     * Check if input contains only ASCII
     * Treats null as all ASCII
     */
    private static boolean isOnlyASCII(String input) {
        if (input == null) {
            return true;
        }
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) > 0x7F) { // CHECKSTYLE IGNORE MagicNumber
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a sorted array contains the specified key
     *
     * @param sortedArray the array to search
     * @param key         the key to find
     * @return {@code true} if the array contains the key
     */
    private static boolean arrayContains(String[] sortedArray, String key) {
        return Arrays.binarySearch(sortedArray, key) >= 0;
    }
}
