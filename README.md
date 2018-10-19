github-bucket
================

Deploy every [GitHub](https://github.com/) repository securely with [Git](https://git-scm.com/) to your [S3](https://aws.amazon.com/s3/) bucket and keep it automatically up to date on push.
Specify a branch, which will be unpacked to S3. You might publish a static website to S3 and make it globally available with [CloudFront](https://aws.amazon.com/cloudfront/).

Take full advantage of GitHub deploy keys, which can be setup per repository.
Finally, pay only per use as this project uses [Lambda](https://aws.amazon.com/lambda/).

## Amazon Web Services ##
- [IAM](https://aws.amazon.com/iam/): Identity and Access Management.
- [SNS](https://aws.amazon.com/sns/): Used as Message Queue to trigger the Lambda function.
- [Lambda](https://aws.amazon.com/lambda/): Used to sync the GitHub repository with S3.
- [S3](https://aws.amazon.com/s3/): Used as data store and file backend.
- [CloudWatch](https://aws.amazon.com/cloudwatch/): Used for viewing logs from SNS and Lambda.
- (Optional) [CloudFront](https://aws.amazon.com/cloudfront/): Used as static website service as it has lower pricing as S3 ([Guide](http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/MigrateS3ToCloudFront.html)).
- (Optional) [Route 53](https://aws.amazon.com/route53/): Route your DNS requests of the custom domain to the closest CloudFront edge server ([Guide](http://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-to-cloudfront-distribution.html)).

## Quickstart ##

Your Amazon Web Services should use the same region! Take also a look at [Dynamic GitHub Actions with AWS Lambda](https://aws.amazon.com/de/blogs/compute/dynamic-github-actions-with-aws-lambda/) to get started with the Lambda deployment options.

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

Download the latest release from the [release section](https://github.com/berlam/github-bucket/releases/latest).
Create a new blank Lambda function. During creation also add the created SNS topic as trigger for your function.
Name the function as you like, e.g. `StaticSiteDeployer` and choose the runtime Java 8 (or higher). Following environment variables should be changed:

- `env_branch`: The branch to watch, default `master`.
- `env_bucket`: The bucket to push to, e.g. `baxterthehacker`.
- `env_github`: The GitHub repository to pull from, e.g. `baxterthehacker/public-repo.git`.

You can also [change the environment variables](http://docs.aws.amazon.com/lambda/latest/dg/env_variables.html) from the AWS console afterwards.

The handler class for Lambda must be configured to: `net.berla.aws.Lambda`.
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

#### Note ####

Java isn't as good as other languages in cold start. It takes a few seconds for the application to boot (~5s).
After that, the repository is initially cloned, which may take longer than one minute (depends on repository size).
In this case you can increase the lambda timeout or upload the initial working tree by yourself on the first run.
All further uploads will be checked file by file against their MD5 checksum.

Files are currently processed inside memory. If you have large files stored inside your repository, you will need to increase the memory size even further.
It is planned to process large files inside the lambda temp directory, to save some memory. But this isn't implemented yet.

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

## Building ##

If you want to build from source, then just trigger [Maven](https://maven.apache.org/) with `mvn clean package` from inside the project root directory. The JAR will be created as `target/github-bucket-*.jar`.

## How does it work? ##

GitHub triggers SNS after one of these [events](https://developer.github.com/v3/activity/events/types/). SNS triggers the Lambda function, which will check for Push-Events on the configured branch. If the branch matches, it will check the S3 bucket for the current state and update it with the GitHub state. The changes will be applied and pushed back to S3.

## Why Java? ##

Lambda officially supports just a few technologies and at the time of writing these were Node.js, Python, C# and Java.
There are some workarounds for other languages, like my personal preference Go, but as we do not control the underlying system this could fail in the future.

Furthermore there is a great implementation of Git completely written in Java by the Eclipse Project which is called jgit.
This allowed me to use the much better deployment keys instead of the plain GitHub API, which also has less features and requires you to add a personal access token for your whole account and not just the repository.
Last but not least the deployment key can be readonly.

## Credits ##

This project was created by [@berlam](https://github.com/berlam).

## License ##

See [LICENSE](LICENSE).

- AWS SDK: [Apache License Version 2.0](https://github.com/aws/aws-sdk-java/blob/master/LICENSE.txt)
- Tika: [Apache License Version 2.0](https://github.com/apache/tika/blob/master/LICENSE.txt)
- JGit: [EDL Version 1.0](https://github.com/eclipse/jgit/blob/master/LICENSE)
