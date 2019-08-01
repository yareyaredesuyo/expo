import * as React from 'react';
import { StyleSheet, ViewProps, ScrollView, Text } from 'react-native';
import Animated from 'react-native-reanimated';

const AnimatedScrollView: typeof ScrollView = Animated.createAnimatedComponent(ScrollView);

interface Props extends ViewProps {

}

interface State {
  offset: Animated.Value<number>,
}

export default class SnappableCarousel extends React.PureComponent<Props, State> {
  readonly state: State = {
    offset: new Animated.Value(0),
  };

  scrollViewHandle?: ScrollView | null;

  handleOnScroll = () => {

  }

  onScrollHandler = Animated.event([
      {
        nativeEvent: {
          contentOffset: { x: this.state.offset }
        }
      }
    ],
    {
      useNativeDriver: true,
      listener: this.handleOnScroll,
    });

  handleOnScrollBeginDrag = () => {

  }

  renderItems = () => {
    return ['Ahoj 1', 'Ahoj 2', 'Ahoj 3', 'Ahoj 4', 'Ahoj 5'].map(this.renderItem);
  }

  renderItem = (item: string) => {
    return (
      <Animated.View style={styles.elementContainer} key={item}>
        <Text style={styles.text}>
          {item}
        </Text>
      </Animated.View>
    )
  }

  render() {
    const { style } = this.props;

    return (
      <AnimatedScrollView
        style={styles.container}
        contentContainerStyle={styles.contentContainer}

        showsHorizontalScrollIndicator={false}
        horizontal
        overScrollMode="never"
        automaticallyAdjustContentInsets={false}
        directionalLockEnabled={true}
        pinchGestureEnabled={false}
        scrollsToTop={false}
        bounces={false}
        ref={r => (this.scrollViewHandle = r)}

        scrollEventThrottle={16}
        onScroll={this.onScrollHandler}
        onScrollBeginDrag={this.handleOnScrollBeginDrag}
      >
        {this.renderItems()}
      </AnimatedScrollView>
    )
  }
}

const styles = StyleSheet.create({
  elementContainer: {
    width: 100,
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 10,
    paddingBottom: 10,
    borderWidth: 2,
  },
  text: {
    color: 'white',
  },
  container: {
    flex: 1,
    flexDirection: 'row',
    backgroundColor: 'rgba(200, 100, 0, 0.5)',
  },
  contentContainer: {
    paddingLeft: 0,
    paddingRight: 0,
  },
});
