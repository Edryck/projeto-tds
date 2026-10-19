import argparse
import base64
import hashlib
import json
import os
import urllib.request

FOLDER_ORDER = ["Auth", "Roles", "Users", "Devices", "Categories", "Products", "Variants", "Stock",
                "Suppliers", "Customers", "Orders", "Coupons", "Finance", "Audit"]
FOLDER_BY_SEGMENT = {
    "stock-entries": "Stock",
    "financial-entries": "Finance",
    "financial-categories": "Finance",
    "financial-accounts": "Finance",
    "cash-closings": "Finance",
    "finance": "Finance",
    "audit-logs": "Audit",
}
MANUAL_FOLDERS = ["Richardson", "Sync"]
METHOD_ORDER = ["get", "post", "put", "patch", "delete"]
NO_TOKEN_OPERATIONS = ["login", "refresh"]
EXAMPLE_UUID = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
WORKSPACE_ID = "wrk_chic_charmosa"
ENVIRONMENT_ID = "env_chic_charmosa_base"
LOGIN_KEY = "post /api/auth/login"
MAX_DEPTH = 5
EXPORT_DATE = "2026-10-16T00:00:00.000Z"
PAGEABLE_PARAMS = ["page", "size", "sort"]


def stable_id(prefix, key):
    return prefix + "_" + hashlib.sha1(key.encode()).hexdigest()[:32]


def load_json(source):
    if source.startswith("http://") or source.startswith("https://"):
        with urllib.request.urlopen(source) as response:
            return json.load(response)
    with open(source, encoding="utf-8") as file:
        return json.load(file)


def resolve_ref(spec, ref):
    node = spec
    for part in ref.lstrip("#/").split("/"):
        node = node[part]
    return node


def sample_value(spec, schema, depth=0):
    if "$ref" in schema:
        if depth > MAX_DEPTH:
            return None
        return sample_value(spec, resolve_ref(spec, schema["$ref"]), depth + 1)
    for key in ("allOf", "oneOf", "anyOf"):
        if key in schema:
            return sample_value(spec, schema[key][0], depth + 1)
    if "enum" in schema:
        return schema["enum"][0]
    kind = schema.get("type")
    if kind == "object" or "properties" in schema:
        return sample_object(spec, schema, depth)
    if kind == "array":
        if depth > MAX_DEPTH:
            return []
        return [sample_value(spec, schema.get("items", {}), depth + 1)]
    if kind == "integer":
        return 1
    if kind == "number":
        return 1.0
    if kind == "boolean":
        return True
    return sample_string(schema)


def sample_object(spec, schema, depth):
    result = {}
    for name, prop in schema.get("properties", {}).items():
        resolved = resolve_ref(spec, prop["$ref"]) if "$ref" in prop else prop
        if resolved.get("readOnly"):
            continue
        if depth > MAX_DEPTH:
            continue
        result[name] = sample_value(spec, prop, depth + 1)
    return result


def sample_string(schema):
    fmt = schema.get("format")
    if fmt == "uuid":
        return EXAMPLE_UUID
    if fmt == "date":
        return "2026-10-16"
    if fmt == "date-time":
        return "2026-10-16T12:00:00Z"
    return "string"


def folder_name(path):
    segment = path.split("/")[2]
    return FOLDER_BY_SEGMENT.get(segment, segment.capitalize())


def query_params(operation):
    result = []
    for param in operation.get("parameters", []):
        if param["in"] != "query":
            continue
        names = PAGEABLE_PARAMS if param["name"] == "pageable" else [param["name"]]
        for name in names:
            result.append({"name": name, "value": "", "disabled": True})
    return result


def build_request(spec, path, method, operation, parent_id, sort_key):
    headers = []
    body = {}
    if "requestBody" in operation:
        content = operation["requestBody"]["content"]["application/json"]
        sample = sample_value(spec, content["schema"])
        body = {"mimeType": "application/json", "text": json.dumps(sample, indent=2)}
        headers.append({"name": "Content-Type", "value": "application/json"})
    if operation.get("operationId") not in NO_TOKEN_OPERATIONS:
        headers.append({"name": "Authorization", "value": "Bearer {{ _.token }}"})
    return {
        "_id": stable_id("req", method + " " + path),
        "_type": "request",
        "parentId": parent_id,
        "name": method.upper() + " " + path,
        "method": method.upper(),
        "url": "{{ _.baseUrl }}" + path.replace("{", "{{ _.").replace("}", " }}"),
        "parameters": query_params(operation),
        "headers": headers,
        "body": body,
        "metaSortKey": sort_key,
    }


def collect_operations(spec):
    grouped = {name: [] for name in FOLDER_ORDER}
    for path, item in spec["paths"].items():
        if path.startswith("/api/sync/"):
            continue
        for method, operation in item.items():
            grouped[folder_name(path)].append((path, method, operation))
    for items in grouped.values():
        items.sort(key=lambda entry: (entry[0], METHOD_ORDER.index(entry[1])))
    return grouped


def build_folders_and_requests(spec):
    resources = []
    for index, name in enumerate(FOLDER_ORDER):
        folder_id = stable_id("fld", name)
        resources.append({
            "_id": folder_id,
            "_type": "request_group",
            "parentId": WORKSPACE_ID,
            "name": name,
            "metaSortKey": index,
        })
    grouped = collect_operations(spec)
    for name in FOLDER_ORDER:
        folder_id = stable_id("fld", name)
        for index, (path, method, operation) in enumerate(grouped[name]):
            resources.append(build_request(spec, path, method, operation, folder_id, index))
    return resources


def path_variables(spec):
    names = set()
    for path in spec["paths"]:
        if path.startswith("/api/sync/"):
            continue
        for part in path.split("/"):
            if part.startswith("{"):
                names.add(part.strip("{}"))
    return names


def build_environment(spec):
    login_id = stable_id("req", LOGIN_KEY)
    path = base64.b64encode(b"$.accessToken").decode()
    token = "{% response 'body', '" + login_id + "', 'b64::" + path + "::46b', 'when-expired', 60 %}"
    data = {"baseUrl": "http://localhost:8080", "token": token}
    for name in sorted(path_variables(spec)):
        data[name] = EXAMPLE_UUID
    return {
        "_id": ENVIRONMENT_ID,
        "_type": "environment",
        "parentId": WORKSPACE_ID,
        "name": "Base Environment",
        "data": data,
        "metaSortKey": 0,
    }


def merge_manual(manual_path):
    if not manual_path or not os.path.exists(manual_path):
        return []
    manual = load_json(manual_path)["resources"]
    by_parent = {}
    for resource in manual:
        by_parent.setdefault(resource.get("parentId"), []).append(resource)
    workspace_ids = [r["_id"] for r in manual if r["_type"] == "workspace"]
    roots = [r for r in manual
             if r["_type"] == "request_group" and r["name"] in MANUAL_FOLDERS and r.get("parentId") in workspace_ids]
    copied = []
    for root in sorted(roots, key=lambda r: MANUAL_FOLDERS.index(r["name"])):
        copied.extend(collect_tree(root, by_parent))
    for resource in copied:
        if resource["_id"] in [r["_id"] for r in roots]:
            resource["parentId"] = WORKSPACE_ID
    return copied


def collect_tree(node, by_parent):
    result = [dict(node)]
    for child in by_parent.get(node["_id"], []):
        result.extend(collect_tree(child, by_parent))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-docs", required=True)
    parser.add_argument("--manual")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    spec = load_json(args.api_docs)
    workspace = {
        "_id": WORKSPACE_ID,
        "_type": "workspace",
        "parentId": None,
        "name": "Chic Charmosa",
        "scope": "collection",
    }
    resources = [workspace, build_environment(spec)]
    resources.extend(build_folders_and_requests(spec))
    resources.extend(merge_manual(args.manual))
    export = {
        "_type": "export",
        "__export_format": 4,
        "__export_date": EXPORT_DATE,
        "__export_source": "generate_insomnia.py",
        "resources": resources,
    }
    with open(args.out, "w", encoding="utf-8") as file:
        json.dump(export, file, indent=2, ensure_ascii=False)
        file.write("\n")


if __name__ == "__main__":
    main()
