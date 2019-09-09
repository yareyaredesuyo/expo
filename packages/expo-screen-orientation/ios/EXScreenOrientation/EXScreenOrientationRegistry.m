// Copyright 2019-present 650 Industries. All rights reserved.

#import <EXScreenOrientation/EXScreenOrientationRegistry.h>
#import <UMCore/UMDefines.h>

@implementation EXScreenOrientationRegistry

UM_REGISTER_SINGLETON_MODULE(EXScreenOrientationRegistry)

- (void)setOrientationMask:(UIInterfaceOrientationMask)orientationMask
          forAppId:(NSString *)appId
{
  NSUserDefaults *userDefaults = [NSUserDefaults standardUserDefaults];
  [userDefaults setInteger:orientationMask forKey:appId];
  [userDefaults synchronize];
}

- (UIInterfaceOrientationMask)getOrientationMaskForAppId:(NSString *)appId
{
  NSUserDefaults *userDefaults = [NSUserDefaults standardUserDefaults];
  if (![userDefaults objectForKey:appId]) {
    return UIInterfaceOrientationMaskAllButUpsideDown;
  }
  return [userDefaults integerForKey:appId];
}

- (BOOL)doesKeyExistForAppId:(NSString *)appId
{
  NSUserDefaults *userDefaults = [NSUserDefaults standardUserDefaults];
  if (![userDefaults objectForKey:appId]) {
    return NO;
  }
  return YES;
}

@end
