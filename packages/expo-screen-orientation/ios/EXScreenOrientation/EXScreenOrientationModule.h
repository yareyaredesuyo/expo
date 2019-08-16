//  Copyright © 2019-present 650 Industries. All rights reserved.

#import <UMCore/UMEventEmitter.h>
#import <UMCore/UMExportedModule.h>
#import <UMCore/UMModuleRegistryConsumer.h>
#import "EXScreenOrientationRegistry.h"

@interface EXScreenOrientationModule : UMExportedModule <UMModuleRegistryConsumer, UMEventEmitter>
typedef NS_ENUM(NSInteger, EXOrientation) {
  EXOrientationPortrait,
  EXOrientationPortraitUp,
  EXOrientationPortraitDown,
  EXOrientationLandscape,
  EXOrientationLandscapeLeft,
  EXOrientationLandscapeRight,
  EXOrientationUnknown
};

typedef NS_ENUM(NSInteger, EXOrientationLock) {
  EXOrientationDefaultLock,
  EXOrientationAllLock,
  EXOrientationPortraitLock,
  EXOrientationPortraitUpLock,
  EXOrientationPortraitDownLock,
  EXOrientationLandscapeLock,
  EXOrientationLandscapeLeftLock,
  EXOrientationLandscapeRightLock,
  EXOrientationOtherLock,
  EXOrientationAllButUpsideDownLock // deprecated
};

- (UIInterfaceOrientationMask)getOrientationMask;
- (void)setOrienatationMask:(UIInterfaceOrientationMask)mask;
- (EXScreenOrientationRegistry *)getRegistry;
+ (UIInterfaceOrientationMask)getSupportedInterfaceOrientationsWithDefault:(UIInterfaceOrientationMask)defaultMask;

@end
