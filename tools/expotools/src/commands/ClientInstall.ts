import chalk from 'chalk';
import { Command } from '@expo/commander';
import spawnAsync from '@expo/spawn-async';
import { Android, Config, Simulator, Versions } from '@expo/xdl';

import { STAGING_HOST } from '../Constants';
import { getNewestSDKVersionAsync } from '../ProjectVersions';
import askForPlatformAsync from '../utils/askForPlatformAsync';
import askForSDKVersionAsync from '../utils/askForSDKVersionAsync';

type ActionOptions = {
  platform?: 'ios' | 'android';
  sdkVersion?: string;
};

async function downloadAndInstallOnIOSAsync(clientUrl: string): Promise<void> {
  if (!await Simulator._isSimulatorInstalledAsync()) {
    console.error(chalk.red('iOS simulator is not installed!'));
    return;
  }

  console.log('Booting up iOS simulator...');

  await Simulator._openAndBootSimulatorAsync();

  console.log('Uninstalling previously installed Expo client...');

  await Simulator._uninstallExpoAppFromSimulatorAsync();

  console.log(
    `Installing Expo client from ${chalk.blue(clientUrl)} on iOS simulator...`,
  );

  const installResult = await Simulator._installExpoOnSimulatorAsync();

  if (installResult.status !== 0) {
    throw new Error('Installing Expo client simulator failed!');
  }

  const appIdentifier = 'host.exp.Exponent';

  console.log(`Launching Expo client with identifier ${chalk.blue(appIdentifier)}...`);

  await spawnAsync('xcrun', ['simctl', 'launch', 'booted', appIdentifier]);
}

async function downloadAndInstallOnAndroidAsync(clientUrl: string): Promise<void> {
  await Android.upgradeExpoAsync(clientUrl);
}

async function action(options: ActionOptions) {
  const platform = await askForPlatformAsync();
  const sdkVersion = options.sdkVersion || await askForSDKVersionAsync(platform, await getNewestSDKVersionAsync(platform));

  if (!sdkVersion) {
    throw new Error(`Unable to find newest SDK version. Try to use ${chalk.yellow('--sdkVersion')} flag.`);
  }

  // Set XDL config to use staging
  Config.api.host = STAGING_HOST;

  const versions = await Versions.versionsAsync();
  const sdkConfiguration = versions && versions.sdkVersions && versions.sdkVersions[sdkVersion];

  if (!sdkConfiguration) {
    throw new Error(`Versions configuration for SDK ${chalk.cyan(sdkVersion)} not found!`);
  }

  const tarballKey = `${platform}ClientUrl`;
  const clientUrl = sdkConfiguration[tarballKey];

  if (!clientUrl) {
    throw new Error(`Client url not found at ${chalk.yellow(tarballKey)} key of versions config!`);
  }

  switch (platform) {
    case 'ios': {
      await downloadAndInstallOnIOSAsync(clientUrl);
      break;
    }
    case 'android': {
      await downloadAndInstallOnAndroidAsync(clientUrl);
      break;
    }
    default: {
      throw new Error(`Platform "${platform}" not implemented!`);
    }
  }

  // Android
  // adb install
}

export default (program: Command) => {
  program
    .command('client-install')
    .alias('ci')
    .description('Installs staging version of the client on iOS simulator, Android emulator or connected Android device.')
    .option('-p, --platform [string]', 'Platform for which the client will be installed.')
    .option('-s, --sdkVersion [string]', 'SDK version of the client to install.')
    .asyncAction(action);
};
