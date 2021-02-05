terraform {
  required_version = "~> 0.14.0"

  backend "http" {
    address = "http://localhost:8080/tf-state/test"
    lock_address = "http://localhost:8080/tf-state/test"
    // lock_method = "LOCK"
    unlock_address = "http://localhost:8080/tf-state/test"
    // unlock_method = "UNLOCK"
  }

  required_providers {
    local = {
      source = "hashicorp/local"
      version = "2.0.0"
    }
  }
}

resource "local_file" "foo" {
  filename = "${path.root}/test.txt"
  content = "bar!"
}
