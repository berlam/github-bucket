github-s3-deploy
================

Deploy every [GitHub](https://github.com/) repository securely with [Git](https://git-scm.com/) to your [S3](https://aws.amazon.com/s3/) bucket and keep it automatically up to date on push.
Specify a branch, which will be unpacked to S3. You might publish a static website to S3 and make it globally available with [CloudFront](https://aws.amazon.com/cloudfront/).

Take full advantage of GitHub deploy keys, which can be setup per repository.
Finally, pay only per use as this project uses [Lambda](https://aws.amazon.com/lambda/).

## AWS services ##
- [IAM](https://aws.amazon.com/iam/): Identity and Access Management.
- [SNS](https://aws.amazon.com/sns/): Used as Message Queue to trigger the Lambda function.
- [Lambda](https://aws.amazon.com/lambda/): Used to sync the GitHub repository with S3.
- [S3](https://aws.amazon.com/s3/): Used as data store and file backend.
- [CloudWatch](https://aws.amazon.com/cloudwatch/): Used for viewing logs from SNS and Lambda.
- (Optional) [CloudFront](https://aws.amazon.com/cloudfront/): Used as static website service as it has lower pricing as S3 ([Guide](http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/MigrateS3ToCloudFront.html)).
- (Optional) [Route 53](https://aws.amazon.com/route53/): Route your DNS requests of the custom domain to the closest CloudFront edge server ([Guide](http://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-to-cloudfront-distribution.html)).

## Quickstart ##

Your AWS services should use the same region! Take also a look at [Dynamic GitHub Actions with AWS Lambda](https://aws.amazon.com/de/blogs/compute/dynamic-github-actions-with-aws-lambda/) to get started with the Lambda deployment options.

![Architecture](/doc/architecture.png)

### SNS ###

Create a new topic and copy the topic ARN.

### IAM ###

Create a user for GitHub. Go to the AWS console, switch to IAM and create a user with following permissions (replace `$ARN` with the SNS ARN):
```JSON
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "sns:Publish"
            ],
            "Sid": "Stmt0000000000000",
            "Resource": [
                "$ARN"
            ],
            "Effect": "Allow"
        }
    ]
}
```

### S3 ###

Create a new bucket (or choose a existing empty bucket). That's it!

### Lambda ###

Create a new blank Lambda function. During creation also add the created SNS topic as trigger for your function.
Name the function as you like, e.g. `StaticSiteDeployer` and choose the runtime Java 8 (or higher). Following environment variables should be changed:

- `env_branch`: The branch to watch, default `master`.
- `env_remote`: The name of the created remote, default `origin`.
- `env_bucket`: The bucket to push to, e.g. `baxterthehacker`.
- `env_github`: The GitHub repository to pull from, e.g. `baxterthehacker/public-repo.git`.

The handler for Lambda must be configured to: `net.berla.aws.Lambda`.
The memory size should at least be 192 MB and can be increased on OutOfMemory-Exceptions. Adjust the timeout to at least one minute.
The role must be configured with following permissions (replace `baxterthehacker` with your bucket).
```JSON
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "arn:aws:logs:*:*:*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:ListBucket"
            ],
            "Resource": "arn:aws:s3:::baxterthehacker"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject"
            ],
            "Resource": "arn:aws:s3:::baxterthehacker/*"
        }
    ]
}
```

### GitHub ###

Create a deploy key and answer the questions after submitting the command:

```Shell
ssh-keygen -t rsa -b 4096
```
Default location for this is: `~/.ssh/id_rsa`

Get the host key of GitHub for security reasons:

```Shell
ssh-keyscan -t rsa github.com > known_hosts
```

Place the generated key as `.ssh/id_rsa` and the known hosts file as `.ssh/known_hosts` in the S3 bucket.

Switch to the settings of you GitHub-Repository and add the deploy key as readonly key.
Go to the `Integrations & services` section and add Amazon SNS.

Enter your AWS access token for this integration. Please remember, that you should restrict the user rights as much as possible in IAM.
Also enter the ARN of the SNS topic and the region of the SNS topic.

## Logs ##

Use CloudWatch for this. You can for example view the SNS request and the Lambda process logs.

## Testing ##

You can start a local debugging session and start the main method in [net.berla.aws.Worker](src/main/java/net/berla/aws/Worker.java).

You can configure the test runtime by changing the [environment properties](src/main/resources/env.properties) or by setting the already mentioned system variables.

## How does it work? ##

GitHub triggers AWS SNS after one of these [events](https://developer.github.com/v3/activity/events/types/). SNS triggers the Lambda function, which will check for Push-Events on the configured branch. If the branch matches, it will check the S3 bucket for the current state and update it with the GitHub state. The changes will be applied and pushed back to S3.

## Why Java? ##

First of all I use the language and technology which fits best to the actual problem. Lambda officially supports just a few technologies and at the time of writing these were Node.js, Python, C# and Java.
There are some workarounds for other languages, like my personal preference Go, but as we do not control the underlying system this could fail in the future.

Furthermore there is a great rebuild of Git for Java by the Eclipse Project which is called jgit. This allowed me to use the much better deployment keys instead of the plain GitHub API, which also has less features and requires you to add a personal access token for your whole account and not just the repository. Last but not least the deployment key can be readonly.

## Credits ##

This project was created by [Matthias Berla](https://github.com/berlam).

## License ##

See [LICENSE](LICENSE).

- AWS SDK: [Apache License Version 2.0](https://github.com/aws/aws-sdk-java/blob/master/LICENSE.txt)
- JGit: [EDL Version 1](https://github.com/eclipse/jgit/blob/master/LICENSE).
