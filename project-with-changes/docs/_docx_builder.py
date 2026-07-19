"""Minimal pure-stdlib OOXML .docx generator. No external dependencies."""
import zipfile
import xml.sax.saxutils as sax

def esc(t):
    return sax.escape(str(t), {'"': "&quot;", "'": "&apos;"})

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

elements = []

def H(level, text):
    elements.append(("heading", level, text))

def P(text, bold=False, italic=False):
    elements.append(("para", text, {"bold": bold, "italic": italic}))

def RUNS(*parts):
    """parts: list of (text, {'bold':bool,'italic':bool,'mono':bool})"""
    elements.append(("runs", parts))

def T(headers, rows):
    elements.append(("table", headers, rows))

def CODE(text):
    elements.append(("code", text))

def BULLETS(items):
    elements.append(("bullets", items))

def SPACER():
    elements.append(("spacer",))

def _run_xml(text, bold=False, italic=False, mono=False, size=None):
    rpr = ""
    if bold:
        rpr += "<w:b/>"
    if italic:
        rpr += "<w:i/>"
    if mono:
        rpr += '<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/><w:sz w:val="18"/><w:szCs w:val="18"/>'
    if size:
        rpr += f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>'
    rpr_xml = f"<w:rPr>{rpr}</w:rPr>" if rpr else ""
    # preserve leading/trailing spaces and manual line breaks
    lines = str(text).split("\n")
    runs = []
    for i, line in enumerate(lines):
        if i > 0:
            runs.append(f'<w:r>{rpr_xml}<w:br/></w:r>')
        runs.append(f'<w:r>{rpr_xml}<w:t xml:space="preserve">{esc(line)}</w:t></w:r>')
    return "".join(runs)

def _heading_xml(level, text):
    style = {1: "Heading1", 2: "Heading2", 3: "Heading3"}[level]
    return f'<w:p><w:pPr><w:pStyle w:val="{style}"/></w:pPr>{_run_xml(text)}</w:p>'

def _para_xml(text, opts):
    return f'<w:p>{_run_xml(text, bold=opts.get("bold", False), italic=opts.get("italic", False))}</w:p>'

def _runs_xml(parts):
    body = "".join(_run_xml(t, **opts) for t, opts in parts)
    return f"<w:p>{body}</w:p>"

def _code_xml(text):
    lines = str(text).split("\n")
    paras = []
    for line in lines:
        paras.append(
            f'<w:p><w:pPr><w:pStyle w:val="Code"/><w:shd w:val="clear" w:fill="F3F3F3"/></w:pPr>'
            f'<w:r><w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/><w:sz w:val="18"/></w:rPr>'
            f'<w:t xml:space="preserve">{esc(line) if line else " "}</w:t></w:r></w:p>'
        )
    return "".join(paras)

def _bullets_xml(items):
    paras = []
    for item in items:
        paras.append(
            f'<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>'
            f'{_run_xml(item)}</w:p>'
        )
    return "".join(paras)

def _table_xml(headers, rows):
    tbl_pr = (
        '<w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/>'
        '<w:tblBorders>'
        '<w:top w:val="single" w:sz="4" w:color="BFBFBF"/>'
        '<w:left w:val="single" w:sz="4" w:color="BFBFBF"/>'
        '<w:bottom w:val="single" w:sz="4" w:color="BFBFBF"/>'
        '<w:right w:val="single" w:sz="4" w:color="BFBFBF"/>'
        '<w:insideH w:val="single" w:sz="4" w:color="BFBFBF"/>'
        '<w:insideV w:val="single" w:sz="4" w:color="BFBFBF"/>'
        '</w:tblBorders></w:tblPr>'
    )
    grid = "<w:tblGrid>" + "".join(f'<w:gridCol w:w="{9000 // max(len(headers),1)}"/>' for _ in headers) + "</w:tblGrid>"

    def cell(text, header=False):
        shd = '<w:shd w:val="clear" w:fill="2F5496"/>' if header else ""
        bold = header
        color = '<w:color w:val="FFFFFF"/>' if header else ""
        rpr = f"<w:rPr>{'<w:b/>' if bold else ''}{color}</w:rPr>" if (bold or color) else ""
        para = f'<w:p><w:pPr>{shd}</w:pPr><w:r>{rpr}<w:t xml:space="preserve">{esc(text)}</w:t></w:r></w:p>'
        tcpr = f'<w:tcPr>{shd}<w:vAlign w:val="center"/></w:tcPr>' if header else '<w:tcPr><w:vAlign w:val="center"/></w:tcPr>'
        return f"<w:tc>{tcpr}{para}</w:tc>"

    header_row = "<w:tr>" + "".join(cell(h, header=True) for h in headers) + "</w:tr>"
    data_rows = "".join("<w:tr>" + "".join(cell(c) for c in row) + "</w:tr>" for row in rows)
    return f"<w:tbl>{tbl_pr}{grid}{header_row}{data_rows}</w:tbl>"

def render_body():
    parts = []
    for el in elements:
        kind = el[0]
        if kind == "heading":
            parts.append(_heading_xml(el[1], el[2]))
        elif kind == "para":
            parts.append(_para_xml(el[1], el[2]))
        elif kind == "runs":
            parts.append(_runs_xml(el[1]))
        elif kind == "table":
            parts.append(_table_xml(el[1], el[2]))
            parts.append('<w:p/>')
        elif kind == "code":
            parts.append(_code_xml(el[1]))
        elif kind == "bullets":
            parts.append(_bullets_xml(el[1]))
        elif kind == "spacer":
            parts.append("<w:p/>")
    return "".join(parts)

DOCUMENT_XML_TEMPLATE = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>
{body}
<w:sectPr>
<w:pgSz w:w="12240" w:h="15840"/>
<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
</w:sectPr>
</w:body>
</w:document>"""

STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:lang w:val="en-US"/></w:rPr></w:rPrDefault>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal">
<w:name w:val="Normal"/>
<w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr>
</w:style>
<w:style w:type="paragraph" w:styleId="Heading1">
<w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
<w:pPr><w:spacing w:before="480" w:after="240"/><w:outlineLvl w:val="0"/></w:pPr>
<w:rPr><w:rFonts w:ascii="Calibri Light" w:hAnsi="Calibri Light"/><w:b/><w:color w:val="1F3864"/><w:sz w:val="36"/></w:rPr>
</w:style>
<w:style w:type="paragraph" w:styleId="Heading2">
<w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
<w:pPr><w:spacing w:before="360" w:after="160"/><w:outlineLvl w:val="1"/></w:pPr>
<w:rPr><w:rFonts w:ascii="Calibri Light" w:hAnsi="Calibri Light"/><w:b/><w:color w:val="2F5496"/><w:sz w:val="28"/></w:rPr>
</w:style>
<w:style w:type="paragraph" w:styleId="Heading3">
<w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
<w:pPr><w:spacing w:before="240" w:after="120"/><w:outlineLvl w:val="2"/></w:pPr>
<w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="24"/></w:rPr>
</w:style>
<w:style w:type="paragraph" w:styleId="Code">
<w:name w:val="Code"/><w:basedOn w:val="Normal"/>
<w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>
<w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/><w:sz w:val="18"/></w:rPr>
</w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph">
<w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/>
</w:style>
<w:style w:type="table" w:styleId="TableGrid">
<w:name w:val="Table Grid"/>
<w:tblPr><w:tblBorders>
<w:top w:val="single" w:sz="4" w:color="auto"/><w:left w:val="single" w:sz="4" w:color="auto"/>
<w:bottom w:val="single" w:sz="4" w:color="auto"/><w:right w:val="single" w:sz="4" w:color="auto"/>
<w:insideH w:val="single" w:sz="4" w:color="auto"/><w:insideV w:val="single" w:sz="4" w:color="auto"/>
</w:tblBorders></w:tblPr>
</w:style>
</w:styles>"""

NUMBERING_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:abstractNum w:abstractNumId="0">
<w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#8226;"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
</w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
</w:numbering>"""

CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>"""

ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>"""

DOCUMENT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
</Relationships>"""

CORE_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>Driving School Backend API Guide</dc:title>
<dc:creator>Backend Team</dc:creator>
</cp:coreProperties>"""

def save(path):
    body = render_body()
    document_xml = DOCUMENT_XML_TEMPLATE.format(body=body)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", CONTENT_TYPES_XML)
        z.writestr("_rels/.rels", ROOT_RELS_XML)
        z.writestr("docProps/core.xml", CORE_XML)
        z.writestr("word/document.xml", document_xml)
        z.writestr("word/styles.xml", STYLES_XML)
        z.writestr("word/numbering.xml", NUMBERING_XML)
        z.writestr("word/_rels/document.xml.rels", DOCUMENT_RELS_XML)
