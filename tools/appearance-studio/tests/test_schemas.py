from __future__ import annotations

import json
import unittest
from pathlib import Path

import jsonschema
import yaml


TOOL_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = TOOL_ROOT.parent.parent


class SchemaTest(unittest.TestCase):
    def validate(self, schema_name: str, document: Path):
        schema = json.loads((TOOL_ROOT / "schema" / schema_name).read_text())
        jsonschema.Draft202012Validator.check_schema(schema)
        jsonschema.validate(yaml.safe_load(document.read_text()), schema)

    def test_registry_and_adopted_cowboy_manifest(self):
        self.validate("registry.schema.json", REPO_ROOT / "content/appearance/registry.yaml")
        self.validate("appearance.schema.json", REPO_ROOT / "content/custom/cowboy_hat/appearance.yaml")

    def test_future_layers_and_look_are_schema_only(self):
        fixtures = TOOL_ROOT / "tests/fixtures"
        self.validate("authoring-layer.schema.json", fixtures / "hair_layer.yaml")
        self.validate("authoring-layer.schema.json", fixtures / "facial_hair_layer.yaml")
        self.validate("look.schema.json", fixtures / "look_mullet_mustache.yaml")
        for path in fixtures.glob("*.yaml"):
            self.assertIsNone(yaml.safe_load(path.read_text())["art"])

    def test_production_look_proposals_match_the_nonshipping_schemas(self):
        proposals = REPO_ROOT / "content/appearance/proposals"
        self.validate("authoring-layer.schema.json", proposals / "future_mullet.yaml")
        self.validate("authoring-layer.schema.json", proposals / "future_mustache.yaml")
        self.validate("look.schema.json", proposals / "future_mullet_mustache.yaml")

    def test_locked_paperdoll_template_schema(self):
        self.validate("paperdoll-template.schema.json", REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml")

    def test_v2_contract_fields_are_required_and_closed(self):
        layer_schema = json.loads((TOOL_ROOT / "schema/authoring-layer.schema.json").read_text())
        layer = yaml.safe_load((REPO_ROOT / "content/appearance/proposals/future_mullet.yaml").read_text())
        layer["propagation"] = "pose-warp"
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.validate(layer, layer_schema)
        layer["propagation"] = "rigid-head"
        layer["kind"] = "hat"
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.validate(layer, layer_schema)

        template_schema = json.loads((TOOL_ROOT / "schema/paperdoll-template.schema.json").read_text())
        template = yaml.safe_load(
            (REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml").read_text()
        )
        del template["coordinate_convention"]
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.validate(template, template_schema)


if __name__ == "__main__":
    unittest.main()
