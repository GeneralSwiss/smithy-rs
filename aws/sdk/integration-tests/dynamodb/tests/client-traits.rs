/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

use aws_sdk_dynamodb as dynamodb;

#[tokio::test]
async fn client_is_debug() {
    let client = dynamodb::Client::from_env();
    assert_eq!(format!("{:?}", client), "foo"));
}
