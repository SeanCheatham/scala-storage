package com.seancheatham.storage.gcloud

import fixtures.BinaryStorageSpec

class GoogleCloudStorageSpec extends BinaryStorageSpec(
  GoogleCloudStorage(),
  "continuous-integration-t-a4540.appspot.com"
) {

}