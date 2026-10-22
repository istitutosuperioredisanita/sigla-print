#!/usr/bin/env python3
"""
Converte i .jrxml dal formato JasperReports 6.x / iReport al formato 7.x.

Uso:  python3 convert.py <cartella-sorgenti> <cartella-output>

- Mantiene la struttura delle cartelle; copia anche i file non-jrxml (immagini, ecc.).
- I report che contengono crosstab / grafici / componenti (table, list...) NON vengono
  convertiti automaticamente: finiscono in <output>/_da_convertire_a_mano/ (invariati)
  e sono elencati in <output>/conversione-python.csv.
- Non modifica mai i sorgenti.
"""
import copy
import csv
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

ELEMENT_KINDS = {"staticText", "textField", "image", "line", "rectangle", "ellipse",
                 "subreport", "frame", "break"}
SINGLE_BAND_SECTIONS = ["background", "title", "pageHeader", "columnHeader", "columnFooter",
                        "pageFooter", "lastPageFooter", "summary", "noData"]


class Unsupported(Exception):
    pass


def strip_ns(e):
    e.tag = e.tag.split("}")[-1]
    for k in list(e.attrib):
        if k.startswith("{") or k.startswith("xmlns"):
            del e.attrib[k]
    for c in e:
        strip_ns(c)


def rn(k):
    """isBold -> bold, isBlankWhenNull -> blankWhenNull, ..."""
    m = re.match(r"is([A-Z])(.*)$", k)
    return m.group(1).lower() + m.group(2) if m else k


def set_attrs(out, src_attrib, mapping=None):
    mapping = mapping or {}
    for k, v in src_attrib.items():
        nk = mapping.get(k, rn(k))
        if k == "isStyledText":
            if v == "true" and "markup" not in src_attrib:
                out.set("markup", "styled")
            continue
        if nk == "stretchType" and v == "RelativeToBandHeight":
            v = "ContainerHeight"  # alias deprecato in 6.x, rimosso in 7
        out.set(nk, v)


def leaf(tag, text, **attrs):
    e = ET.Element(tag, attrs)
    e.text = text
    return e


def clone(e):
    """Copia profonda con rinomina degli attributi isXxx."""
    n = ET.Element(e.tag)
    set_attrs(n, e.attrib)
    n.text = e.text
    for c in e:
        n.append(clone(c))
    return n


# ---------------------------------------------------------------- stili
def conv_style(s):
    n = ET.Element("style")
    set_attrs(n, s.attrib, {"hAlign": "hTextAlign", "vAlign": "vTextAlign"})
    for c in s:
        if c.tag == "conditionalStyle":
            cs = ET.SubElement(n, "conditionalStyle")
            for cc in c:
                if cc.tag == "conditionExpression":
                    cs.append(clone(cc))
            for cc in c:
                if cc.tag == "style":
                    set_attrs(cs, cc.attrib, {"hAlign": "hTextAlign", "vAlign": "vTextAlign"})
                    for k in cc:
                        cs.append(clone(k))
        else:
            n.append(clone(c))
    return n


# ---------------------------------------------------------------- elementi
def merge_text_element(te, out):
    set_attrs(out, te.attrib, {"textAlignment": "hTextAlign", "verticalAlignment": "vTextAlign"})
    line_spacing = out.attrib.pop("lineSpacing", None)
    para = None
    for c in te:
        if c.tag == "font":
            set_attrs(out, c.attrib, {"size": "fontSize"})
        elif c.tag == "paragraph":
            para = clone(c)
    if line_spacing and (para is None or "lineSpacing" not in para.attrib):
        if para is None:
            para = ET.Element("paragraph")
        para.set("lineSpacing", line_spacing)
    if para is not None:
        out.append(para)


def conv_dataset_run(dr):
    n = ET.Element("datasetRun")
    set_attrs(n, dr.attrib)
    for c in dr:
        if c.tag == "datasetParameter":
            p = ET.SubElement(n, "datasetParameter", {"name": c.attrib.get("name", "")})
            ex = c.find("datasetParameterExpression")
            if ex is not None:
                p.append(leaf("expression", ex.text))
        else:
            n.append(clone(c))
    return n


def conv_cell(c, tag):
    """Cella di table (tableHeader, detailCell...) o contenuto di list."""
    n = ET.Element(tag)
    set_attrs(n, c.attrib)
    for k in c:
        if k.tag in ("box", "property", "printWhenExpression"):
            n.append(clone(k))
        else:
            conv_elem(k, n)
    return n


def conv_component(e, parent):
    comp = None
    for c in e:
        if c.tag != "reportElement":
            comp = c
    if comp is None or comp.tag not in ("list", "table"):
        raise Unsupported("componentElement" + ("/" + comp.tag if comp is not None else ""))
    out = ET.SubElement(parent, "element", {"kind": comp.tag})
    re_ = e.find("reportElement")
    if re_ is not None:
        set_attrs(out, re_.attrib)
    set_attrs(out, comp.attrib)
    deferred = [clone(c) for c in re_] if re_ is not None else []
    for c in comp:
        if c.tag == "datasetRun":
            deferred.append(conv_dataset_run(c))
        elif comp.tag == "list" and c.tag == "listContents":
            deferred.append(conv_cell(c, "contents"))
        elif comp.tag == "table" and c.tag == "column":
            col = ET.Element("column", {"kind": "single"})
            set_attrs(col, c.attrib)
            for k in c:
                if k.tag in ("tableHeader", "tableFooter", "columnHeader", "columnFooter", "detailCell"):
                    col.append(conv_cell(k, k.tag))
                elif k.tag == "property":
                    col.append(clone(k))
                else:
                    raise Unsupported("table/column/" + k.tag)
            deferred.append(col)
        else:
            raise Unsupported(comp.tag + "/" + c.tag)
    for d in deferred:
        out.append(d)


def conv_elem(e, parent):
    t = e.tag
    if t == "componentElement":
        conv_component(e, parent)
        return
    if t == "elementGroup":
        for c in e:
            conv_elem(c, parent)
        return
    if t not in ELEMENT_KINDS:
        raise Unsupported(t)

    out = ET.SubElement(parent, "element", {"kind": t})
    mapping = {}
    if t == "image":
        mapping = {"hAlign": "hImageAlign", "vAlign": "vImageAlign"}

    own = dict(e.attrib)
    if own.get("isStretchWithOverflow") == "true" and "textAdjust" not in own:
        out.set("textAdjust", "StretchHeight")
    own.pop("isStretchWithOverflow", None)

    re_ = e.find("reportElement")
    if re_ is not None:
        set_attrs(out, re_.attrib)
    set_attrs(out, own, mapping)

    # attributi prima, figli dopo
    deferred = []
    if re_ is not None:
        for c in re_:
            deferred.append(clone(c))  # property, printWhenExpression

    for c in e:
        ct = c.tag
        if ct == "reportElement":
            continue
        elif ct == "textElement":
            merge_text_element(c, out)
        elif ct == "graphicElement":
            if "fill" in c.attrib:
                out.set("fill", c.attrib["fill"])
            for p in c:
                deferred.append(clone(p))
        elif ct in ("textFieldExpression", "imageExpression", "subreportExpression"):
            deferred.append(leaf("expression", c.text))
        elif ct == "subreportParameter":
            p = ET.Element("parameter", {"name": c.attrib.get("name", "")})
            ex = c.find("subreportParameterExpression")
            if ex is not None:
                p.append(leaf("expression", ex.text))
            deferred.append(p)
        elif ct == "box":
            deferred.append(clone(c))
        elif t == "frame" and (ct in ELEMENT_KINDS or ct == "elementGroup"):
            tmp = ET.Element("tmp")
            conv_elem(c, tmp)
            deferred.extend(list(tmp))
        elif ct in ("text", "patternExpression", "anchorNameExpression",
                    "hyperlinkReferenceExpression", "hyperlinkAnchorExpression",
                    "hyperlinkPageExpression", "hyperlinkTooltipExpression",
                    "hyperlinkWhenExpression", "hyperlinkParameter",
                    "connectionExpression", "dataSourceExpression",
                    "parametersMapExpression", "returnValue"):
            deferred.append(clone(c))
        else:
            raise Unsupported(t + "/" + ct)
    for d in deferred:
        out.append(d)


def conv_band(band):
    """band 6.x -> band 7 (usata per detail e per group header/footer)."""
    n = ET.Element("band")
    set_attrs(n, band.attrib)
    fill_band(band, n)
    return n


def fill_band(src_band, dst):
    for c in src_band:
        if c.tag in ("printWhenExpression", "property"):
            dst.append(clone(c))
        else:
            conv_elem(c, dst)


def conv_section(sec):
    n = ET.Element(sec.tag)
    band = sec.find("band")
    if band is not None:
        set_attrs(n, band.attrib)
        fill_band(band, n)
    return n


# ---------------------------------------------------------------- dichiarazioni
def conv_declaration(c, parent):
    t = c.tag
    if t == "import":
        # formato 7: il valore e' il testo dell'elemento, l'attributo value viene ignorato
        parent.append(leaf("import", c.attrib.get("value", c.text)))
    elif t in ("property", "template"):
        parent.append(clone(c))
    elif t == "style":
        parent.append(conv_style(c))
    elif t == "parameter":
        n = ET.SubElement(parent, "parameter")
        set_attrs(n, c.attrib)
        for k in c:
            if k.tag == "parameterDescription":
                n.append(leaf("description", k.text))
            else:
                n.append(clone(k))
    elif t == "queryString":
        parent.append(leaf("query", c.text, language=c.attrib.get("language", "sql")))
    elif t == "field":
        n = ET.SubElement(parent, "field")
        set_attrs(n, c.attrib)
        for k in c:
            if k.tag == "fieldDescription":
                n.append(leaf("description", k.text))
            else:
                n.append(clone(k))
    elif t == "sortField":
        parent.append(clone(c))
    elif t == "variable":
        n = ET.SubElement(parent, "variable")
        set_attrs(n, c.attrib)
        for k in c:
            if k.tag == "variableExpression":
                n.append(leaf("expression", k.text))
            else:
                n.append(clone(k))
    elif t == "filterExpression":
        parent.append(clone(c))
    elif t == "group":
        n = ET.SubElement(parent, "group")
        set_attrs(n, c.attrib)
        for k in c:
            if k.tag == "groupExpression":
                n.append(leaf("expression", k.text))
            elif k.tag in ("groupHeader", "groupFooter"):
                h = ET.SubElement(n, k.tag)
                for b in k:
                    if b.tag == "band":
                        h.append(conv_band(b))
                    else:
                        raise Unsupported(k.tag + "/" + b.tag)
            else:
                raise Unsupported("group/" + k.tag)
    elif t == "subDataset":
        n = ET.SubElement(parent, "subDataset")
        set_attrs(n, c.attrib)
        for k in c:
            conv_declaration(k, n)
    else:
        raise Unsupported(t)


def conv_report(root):
    n = ET.Element("jasperReport")
    set_attrs(n, root.attrib)
    if "language" not in n.attrib:
        n.set("language", "java")
    for c in root:
        t = c.tag
        if t in SINGLE_BAND_SECTIONS:
            n.append(conv_section(c))
        elif t == "detail":
            d = ET.SubElement(n, "detail")
            for b in c:
                d.append(conv_band(b))
        else:
            conv_declaration(c, n)
    return n


# ---------------------------------------------------------------- scrittura
def esc_attr(v):
    return (v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;").replace("\n", "&#10;").replace("\r", "&#13;")
             .replace("\t", "&#9;"))


def cdata(t):
    return "<![CDATA[" + t.replace("]]>", "]]]]><![CDATA[>") + "]]>"


def ser(e, lvl, out):
    pad = "\t" * lvl
    a = "".join(' %s="%s"' % (k, esc_attr(v)) for k, v in e.attrib.items())
    kids = list(e)
    if kids:
        out.append("%s<%s%s>\n" % (pad, e.tag, a))
        for k in kids:
            ser(k, lvl + 1, out)
        out.append("%s</%s>\n" % (pad, e.tag))
    elif e.text is not None and (e.text.strip() != "" or e.tag == "text"):
        out.append("%s<%s%s>%s</%s>\n" % (pad, e.tag, a, cdata(e.text), e.tag))
    else:
        out.append("%s<%s%s/>\n" % (pad, e.tag, a))


def convert_file(src, dst):
    tree = ET.parse(src)
    root = tree.getroot()
    strip_ns(root)
    new = conv_report(root)
    out = ['<?xml version="1.0" encoding="UTF-8"?>\n']
    ser(new, 0, out)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(out))


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    src_dir, out_dir = (os.path.abspath(p) for p in sys.argv[1:3])
    manual_dir = os.path.join(out_dir, "_da_convertire_a_mano")
    rows, ok, ko = [], 0, 0
    for dp, dn, fn in os.walk(src_dir):
        dn[:] = [d for d in dn if d != ".git" and os.path.join(dp, d) != out_dir]
        for f in sorted(fn):
            s = os.path.join(dp, f)
            rel = os.path.relpath(s, src_dir)
            if f.lower().endswith(".jrxml"):
                try:
                    convert_file(s, os.path.join(out_dir, rel))
                    ok += 1
                    rows.append((rel, "CONVERTITO", ""))
                except Unsupported as u:
                    ko += 1
                    rows.append((rel, "MANUALE", "elemento non gestito: %s" % u))
                    d = os.path.join(manual_dir, rel)
                    os.makedirs(os.path.dirname(d), exist_ok=True)
                    shutil.copy2(s, d)
                except Exception as ex:  # XML malformato ecc.
                    ko += 1
                    rows.append((rel, "ERRORE", "%s: %s" % (type(ex).__name__, ex)))
            else:
                d = os.path.join(out_dir, rel)
                os.makedirs(os.path.dirname(d), exist_ok=True)
                shutil.copy2(s, d)
    with open(os.path.join(out_dir, "conversione-python.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["file", "esito", "nota"])
        w.writerows(rows)
    print("Convertiti: %d   Da fare a mano / errori: %d" % (ok, ko))
    for r in rows:
        if r[1] != "CONVERTITO":
            print("  %-8s %s  (%s)" % (r[1], r[0], r[2]))


if __name__ == "__main__":
    main()