package com.adobe.epubcheck.overlay;

import java.util.Map;
import java.util.Set;

import com.adobe.epubcheck.api.EPUBLocation;
import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.opf.OPFChecker30;
import com.adobe.epubcheck.opf.ValidationContext;
import com.adobe.epubcheck.opf.XRefChecker;
import com.adobe.epubcheck.util.EpubConstants;
import com.adobe.epubcheck.util.HandlerUtil;
import com.adobe.epubcheck.util.PathUtil;
import com.adobe.epubcheck.vocab.StructureVocab;
import com.adobe.epubcheck.vocab.Vocab;
import com.adobe.epubcheck.vocab.VocabUtil;
import com.adobe.epubcheck.xml.XMLElement;
import com.adobe.epubcheck.xml.XMLHandler;
import com.adobe.epubcheck.xml.XMLParser;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class OverlayHandler implements XMLHandler
{

  private static Map<String, Vocab> RESERVED_VOCABS = ImmutableMap.<String, Vocab> of("",
      StructureVocab.VOCAB);
  private static Map<String, Vocab> KNOWN_VOCAB_URIS = ImmutableMap.of();
  private static Set<String> DEFAULT_VOCAB_URIS = ImmutableSet.of(StructureVocab.URI);

  private final ValidationContext context;
  private final String path;
  private final Report report;
  private final XMLParser parser;

  private boolean checkedUnsupportedXMLVersion;

  private Map<String, Vocab> vocabs = RESERVED_VOCABS;

  public OverlayHandler(ValidationContext context, XMLParser parser)
  {
    this.context = context;
    this.path = context.path;
    this.report = context.report;
    this.parser = parser;
    checkedUnsupportedXMLVersion = false;
  }

  public void startElement()
  {
    if (!checkedUnsupportedXMLVersion)
    {
      HandlerUtil.checkXMLVersion(parser);
      checkedUnsupportedXMLVersion = true;
    }

    XMLElement e = parser.getCurrentElement();
    String name = e.getName();

    switch (name) {
      case "smil":
        vocabs = VocabUtil.parsePrefixDeclaration(
            e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "prefix"), RESERVED_VOCABS,
            KNOWN_VOCAB_URIS, DEFAULT_VOCAB_URIS, report,
            EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()));
        break;
        
      case "body":
      case "seq":
      case "par":
        processGlobalAttrs(e);
        break;
       
      case "text":
        processSrc(e);
        break;
      
      case "audio":
        processRef(e.getAttribute("src"), XRefChecker.Type.AUDIO);
        break;
    }
  }

  private void checkType(String type)
  {
    VocabUtil.parsePropertyList(type, vocabs, context,
        EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()));
  }

  private void processSrc(XMLElement e)
  {
    processRef(e.getAttribute("src"), XRefChecker.Type.HYPERLINK);

  }

  private void processRef(String ref, XRefChecker.Type type)
  {
    if (ref != null && context.xrefChecker.isPresent())
    {
      ref = PathUtil.resolveRelativeReference(path, ref);
      if (type == XRefChecker.Type.AUDIO)
      {
        String mimeType = context.xrefChecker.get().getMimeType(ref);
        if (mimeType != null && !OPFChecker30.isBlessedAudioType(mimeType))
        {
          report.message(MessageId.MED_005, EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()), ref, mimeType);
        }
      }
      else {
        checkFragment(ref);
      }
      context.xrefChecker.get().registerReference(path, parser.getLineNumber(),
          parser.getColumnNumber(), ref, type);
    }
  }

  private void processGlobalAttrs(XMLElement e)
  {
    if (!e.getName().equals("audio")) {
      processRef(e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "textref"),
          XRefChecker.Type.HYPERLINK);
    }
    checkType(e.getAttributeNS(EpubConstants.EpubTypeNamespaceUri, "type"));
  }

  public void characters(char[] chars, int arg1, int arg2)
  {
  }

  public void endElement()
  {
  }

  public void ignorableWhitespace(char[] chars, int arg1, int arg2)
  {
  }

  public void processingInstruction(String arg0, String arg1)
  {
  }
  
  private void checkFragment(String ref) {
  
    if (ref.indexOf("#") == -1) {
      // must include a fragid
      report.message(MessageId.MED_014, EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()));
    }
    
    else {
      String frag = PathUtil.getFragment(ref.trim());
      if (Strings.isNullOrEmpty(frag)) {
        // empty fragid # not allowed
        report.message(MessageId.MED_015, EPUBLocation.create(path, parser.getLineNumber(), parser.getColumnNumber()));
      }
    }
  }
}
