#!/usr/bin/env python3
"""Instala el widget de Control de Finanzas en el proyecto Android generado.

El repo NO tiene la carpeta android/ commiteada: el workflow la genera con
`npx cap add android` + `npx cap sync` en cada build. Este script corre
DESPUES de ese paso y hace cuatro cosas:

1. Copia widget/android/src/main/{java,res} a android/app/src/main/.
2. Parchea android/app/src/main/AndroidManifest.xml (si falta):
   - declara el receiver del widget (FinanzasWidgetProvider) con su
     android.appwidget.provider,
   - declara el servicio de vistas remotas (FinanzasWidgetService) con el
     permiso BIND_REMOTEVIEWS, que es lo que permite que el contenido del
     widget se deslice arriba/abajo (ListView), y
   - agrega a MainActivity el intent-filter del enlace controlfinanzas://widget/...
     para que el toque en el widget abra la app con el formulario prellenado.
3. Declara en el manifiesto <queries> con el intent ACTION_IMAGE_CAPTURE,
   para que la foto del ticket con capture="environment" abra la cámara:
   en Android 11+ sin esa declaración el WebView de Capacitor no resuelve
   la app de cámara y el botón de foto solo abre la galería. No es un
   permiso (el intent lo resuelve la app de cámara del sistema), por eso
   NO se declara android.permission.CAMERA.

Es idempotente: si ya existen el receiver, el servicio, el intent-filter
o la declaración de <queries>, no duplica nada.
Uso: python3 widget/scripts/install-widget.py  (desde la raiz del repo)
"""
import os
import shutil
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)


def a(name):
    return "{%s}%s" % (ANDROID_NS, name)


def copy_tree(src, dst):
    for root, dirs, files in os.walk(src):
        rel = os.path.relpath(root, src)
        target = dst if rel == "." else os.path.join(dst, rel)
        os.makedirs(target, exist_ok=True)
        for f in files:
            shutil.copy2(os.path.join(root, f), os.path.join(target, f))


def patch_manifest(path):
    tree = ET.parse(path)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        raise SystemExit("AndroidManifest.xml sin <application>: " + path)

    changed = False

    # 1) Receiver del widget
    has_receiver = any(
        (el.get(a("name")) or "").endswith("FinanzasWidgetProvider")
        for el in app.findall("receiver")
    )
    if not has_receiver:
        receiver = ET.SubElement(app, "receiver")
        receiver.set(a("name"), ".widget.FinanzasWidgetProvider")
        receiver.set(a("exported"), "true")
        receiver.set(a("label"), "Control de Finanzas")
        filt = ET.SubElement(receiver, "intent-filter")
        act = ET.SubElement(filt, "action")
        act.set(a("name"), "android.appwidget.action.APPWIDGET_UPDATE")
        meta = ET.SubElement(receiver, "meta-data")
        meta.set(a("name"), "android.appwidget.provider")
        meta.set(a("resource"), "@xml/finanzas_widget_info")
        changed = True

    # 2) Servicio de vistas remotas (ListView desplazable del widget)
    has_service = any(
        (el.get(a("name")) or "").endswith("FinanzasWidgetService")
        for el in app.findall("service")
    )
    if not has_service:
        svc = ET.SubElement(app, "service")
        svc.set(a("name"), ".widget.FinanzasWidgetService")
        svc.set(a("permission"), "android.permission.BIND_REMOTEVIEWS")
        svc.set(a("exported"), "false")
        changed = True

    # 3) Intent-filter del enlace controlfinanzas://widget/... en MainActivity
    for activity in app.findall("activity"):
        name = activity.get(a("name")) or ""
        if "MainActivity" not in name:
            continue
        has_link = False
        for filt in activity.findall("intent-filter"):
            for data in filt.findall("data"):
                if data.get(a("scheme")) == "controlfinanzas":
                    has_link = True
        if not has_link:
            filt = ET.SubElement(activity, "intent-filter")
            act = ET.SubElement(filt, "action")
            act.set(a("name"), "android.intent.action.VIEW")
            cat = ET.SubElement(filt, "category")
            cat.set(a("name"), "android.intent.category.DEFAULT")
            cat2 = ET.SubElement(filt, "category")
            cat2.set(a("name"), "android.intent.category.BROWSABLE")
            data = ET.SubElement(filt, "data")
            data.set(a("scheme"), "controlfinanzas")
            data.set(a("host"), "widget")
            changed = True

    # 4) Visibilidad de la cámara para la foto del ticket (Android 11+).
    #    El input "Tomar foto" lleva capture="environment": Capacitor lanza
    #    el intent ACTION_IMAGE_CAPTURE y antes verifica con resolveActivity
    #    que haya una app de cámara. En Android 11+ ese chequeo devuelve
    #    null si el manifiesto no declara <queries> con ese intent, y
    #    Capacitor cae al selector de archivos (solo galería), que es el bug
    #    reportado. No es un permiso: el intent lo atiende la app de cámara
    #    del sistema, así que NO se declara android.permission.CAMERA
    #    (declararlo solo agregaría un aviso que, si se niega, cierra el
    #    selector sin abrir nada).
    has_cam_query = False
    for q in root.findall("queries"):
        for intent in q.findall("intent"):
            for act in intent.findall("action"):
                if act.get(a("name")) == "android.media.action.IMAGE_CAPTURE":
                    has_cam_query = True
    if not has_cam_query:
        queries = ET.SubElement(root, "queries")
        intent = ET.SubElement(queries, "intent")
        act = ET.SubElement(intent, "action")
        act.set(a("name"), "android.media.action.IMAGE_CAPTURE")
        changed = True

    if changed:
        tree.write(path, encoding="utf-8", xml_declaration=True)
        print("AndroidManifest.xml actualizado: " + path)
    else:
        print("AndroidManifest.xml ya tenia el widget: sin cambios")


def main():
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    src_main = os.path.join(repo, "widget", "android", "src", "main")
    dst_main = os.path.join(repo, "android", "app", "src", "main")
    if not os.path.isdir(src_main):
        raise SystemExit("No existe: " + src_main)
    if not os.path.isdir(dst_main):
        raise SystemExit(
            "No existe el proyecto Android generado: " + dst_main
            + " (corre primero `npx cap add android` y `npx cap sync`)"
        )
    copy_tree(os.path.join(src_main, "java"), os.path.join(dst_main, "java"))
    copy_tree(os.path.join(src_main, "res"), os.path.join(dst_main, "res"))
    print("Archivos del widget copiados a " + dst_main)
    patch_manifest(os.path.join(dst_main, "AndroidManifest.xml"))


if __name__ == "__main__":
    main()
