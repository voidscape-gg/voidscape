from __future__ import annotations

import unittest

from appearance_studio.evidence_contract import require_current_evidence_contract


class EvidenceContractTest(unittest.TestCase):
    def test_v1_schema_is_explicitly_revoked(self):
        with self.assertRaisesRegex(ValueError, "voidscape-draft-look/v1.*revoked"):
            require_current_evidence_contract({"schema": "voidscape-draft-look/v1"})

    def test_v2_requires_the_calibrated_geometry_contract(self):
        with self.assertRaisesRegex(ValueError, "geometry contract None is unsupported"):
            require_current_evidence_contract({"schema": "voidscape-draft-look/v2"})

        require_current_evidence_contract({
            "schema": "voidscape-draft-look/v2",
            "geometryContract": "voidscape-paperdoll-geometry/v2",
        })


if __name__ == "__main__":
    unittest.main()
