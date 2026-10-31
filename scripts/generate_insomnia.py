import argparse
import base64
import hashlib
import json
import os
import urllib.request

FOLDER_ORDER = ["Auth", "Roles", "Users", "Devices", "Categories", "Products", "Variants", "Stock",
                "Suppliers", "Customers", "Orders", "Coupons", "Finance", "Audit", "Cancellations", "Store Settings"]
FOLDER_BY_SEGMENT = {
    "stock-entries": "Stock",
    "financial-entries": "Finance",
    "financial-categories": "Finance",
    "financial-accounts": "Finance",
    "cash-closings": "Finance",
    "finance": "Finance",
    "audit-logs": "Audit",
    "reports": "Finance",
    "cancellation-requests": "Cancellations",
    "store-settings": "Store Settings",
}
MANUAL_FOLDERS = ["Richardson", "Sync"]
ADMIN_EMAIL = "admin@chiccharmosa.com.br"
ADMIN_PASSWORD = "Admin@123"
VIEWER_EMAIL = "viewer@chiccharmosa.com.br"
VIEWER_PASSWORD = "Viewer@123"
VIEWER_LOGIN_KEY = "post /api/auth/login viewer"
FLOW_FOLDERS = ["Security", "Flow - Sale", "Flow - Cancellation", "Flow - Catalog CRUD"]
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
        content = operation["requestBody"]["content"].get("application/json")
        if content:
            sample = sample_value(spec, content["schema"])
            if operation.get("operationId") == "login":
                sample["email"] = ADMIN_EMAIL
                sample["password"] = ADMIN_PASSWORD
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


def uid(number):
    return "aaaaaaaa-0000-4000-8000-%012d" % number


def flow_request(folder, key, name, method, path, sort_key, body=None, auth="admin"):
    headers = []
    request_body = {}
    if body is not None:
        request_body = {"mimeType": "application/json", "text": json.dumps(body, indent=2)}
        headers.append({"name": "Content-Type", "value": "application/json"})
    if auth == "admin":
        headers.append({"name": "Authorization", "value": "Bearer {{ _.token }}"})
    if auth == "viewer":
        headers.append({"name": "Authorization", "value": "Bearer {{ _.viewerToken }}"})
    if auth == "invalid":
        headers.append({"name": "Authorization", "value": "Bearer invalid.token.value"})
    return {
        "_id": stable_id("req", key),
        "_type": "request",
        "parentId": stable_id("fld", folder),
        "name": name,
        "method": method,
        "url": "{{ _.baseUrl }}" + path,
        "parameters": [],
        "headers": headers,
        "body": request_body,
        "metaSortKey": sort_key,
    }


def order_body(order_id, item_id, quantity):
    return {
        "id": order_id,
        "customerId": uid(40),
        "note": "Flow test order",
        "shippingCost": 10.0,
        "items": [{"id": item_id, "variantId": uid(30), "quantity": quantity}],
    }


def security_steps():
    login = {"email": VIEWER_EMAIL, "password": VIEWER_PASSWORD, "deviceId": uid(90), "deviceName": "viewer device"}
    return [
        ("401 - list products without token", "GET", "/api/products", None, "none"),
        ("401 - create category without token", "POST", "/api/categories", {"name": "No token"}, "none"),
        ("401 - current user without token", "GET", "/api/auth/me", None, "none"),
        ("401 - list products with invalid token", "GET", "/api/products", None, "invalid"),
        ("Setup - create VIEWER role (product:view only)", "POST", "/api/roles",
         {"id": uid(91), "name": "VIEWER", "permissionCodes": ["product:view"]}, "admin"),
        ("Setup - create viewer user", "POST", "/api/users",
         {"id": uid(92), "email": VIEWER_EMAIL, "password": VIEWER_PASSWORD, "name": "Viewer",
          "roleId": uid(91), "active": True}, "admin"),
        ("Viewer - login", "POST", "/api/auth/login", login, "none", VIEWER_LOGIN_KEY),
        ("200 - viewer lists products", "GET", "/api/products", None, "viewer"),
        ("403 - viewer creates category", "POST", "/api/categories", {"name": "Forbidden"}, "viewer"),
        ("403 - viewer lists users", "GET", "/api/users", None, "viewer"),
        ("403 - viewer reads finance summary", "GET", "/api/financial-entries/summary?accountId=30000000-0000-0000-0000-000000000001&from=2026-10-01&to=2026-10-31", None, "viewer"),
    ]


def sale_steps():
    return [
        ("1 - current user", "GET", "/api/auth/me", None),
        ("2 - create category", "POST", "/api/categories", {"id": uid(10), "name": "Flow Dresses"}),
        ("3 - create product", "POST", "/api/products",
         {"id": uid(20), "categoryId": uid(10), "name": "Flow Summer Dress", "description": "Flow test product"}),
        ("4 - create variant", "POST", "/api/products/" + uid(20) + "/variants",
         {"id": uid(30), "color": "Red", "size": "M", "model": "Midi", "price": 120.0, "cost": 60.0}),
        ("5 - stock entry (+10 units)", "POST", "/api/stock-entries",
         {"id": uid(50), "entryDate": "2026-10-31", "note": "Flow stock",
          "items": [{"id": uid(51), "variantId": uid(30), "quantity": 10, "unitCost": 60.0}]}),
        ("6 - variant balance is 10", "GET", "/api/variants/" + uid(30), None),
        ("7 - create customer", "POST", "/api/customers",
         {"id": uid(40), "name": "Flow Customer", "phone": "41999990000", "email": "flow@example.com"}),
        ("8 - create order (PENDING)", "POST", "/api/orders", order_body(uid(60), uid(61), 2)),
        ("9 - get order (links: pay, cancel)", "GET", "/api/orders/" + uid(60), None),
        ("10 - pay order", "POST", "/api/orders/" + uid(60) + "/pay", {"method": "PIX"}),
        ("11 - ship order", "POST", "/api/orders/" + uid(60) + "/ship",
         {"method": "POST_OFFICE", "trackingCode": "BR123456789"}),
        ("12 - deliver order", "POST", "/api/orders/" + uid(60) + "/deliver", None),
        ("13 - get order (DELIVERED)", "GET", "/api/orders/" + uid(60), None),
        ("14 - variant movements (-2 sale)", "GET", "/api/variants/" + uid(30) + "/movements", None),
        ("15 - finance summary", "GET", "/api/financial-entries/summary?accountId=30000000-0000-0000-0000-000000000001&from=2026-10-01&to=2026-10-31", None),
    ]


def cancellation_steps():
    return [
        ("1 - create order (PENDING)", "POST", "/api/orders", order_body(uid(70), uid(71), 1)),
        ("2 - cancel order", "POST", "/api/orders/" + uid(70) + "/cancel", None),
        ("3 - get order (CANCELLED)", "GET", "/api/orders/" + uid(70), None),
        ("4 - variant movements (sale reversed)", "GET", "/api/variants/" + uid(30) + "/movements", None),
    ]


def catalog_crud_steps():
    return [
        ("1 - create supplier", "POST", "/api/suppliers", {"id": uid(80), "name": "Flow Supplier"}),
        ("2 - get supplier", "GET", "/api/suppliers/" + uid(80), None),
        ("3 - list suppliers by name", "GET", "/api/suppliers?name=Flow", None),
        ("4 - update supplier", "PUT", "/api/suppliers/" + uid(80), {"id": uid(80), "name": "Flow Supplier Updated"}),
        ("5 - delete supplier", "DELETE", "/api/suppliers/" + uid(80), None),
        ("6 - get supplier (404)", "GET", "/api/suppliers/" + uid(80), None),
    ]


def build_flows_and_security():
    resources = []
    for index, name in enumerate(FLOW_FOLDERS):
        resources.append({
            "_id": stable_id("fld", name),
            "_type": "request_group",
            "parentId": WORKSPACE_ID,
            "name": name,
            "metaSortKey": len(FOLDER_ORDER) + index,
        })
    for index, step in enumerate(security_steps()):
        key = step[5] if len(step) > 5 else "Security " + step[0]
        resources.append(flow_request("Security", key, step[0], step[1], step[2], index, step[3], step[4]))
    flows = [("Flow - Sale", sale_steps()), ("Flow - Cancellation", cancellation_steps()),
             ("Flow - Catalog CRUD", catalog_crud_steps())]
    for folder, steps in flows:
        for index, (name, method, path, body) in enumerate(steps):
            resources.append(flow_request(folder, folder + " " + name, name, method, path, index, body))
    return resources


def build_environment(spec):
    login_id = stable_id("req", LOGIN_KEY)
    path = base64.b64encode(b"$.accessToken").decode()
    token = "{% response 'body', '" + login_id + "', 'b64::" + path + "::46b', 'when-expired', 60 %}"
    viewer_id = stable_id("req", VIEWER_LOGIN_KEY)
    viewer_token = "{% response 'body', '" + viewer_id + "', 'b64::" + path + "::46b', 'when-expired', 60 %}"
    data = {"baseUrl": "http://localhost:8080", "token": token, "viewerToken": viewer_token}
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
    resources.extend(build_flows_and_security())
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
